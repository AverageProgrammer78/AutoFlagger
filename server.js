const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const csv = require('csv-parser');

const app = express();
const PORT = process.env.PORT || 3001;

// IMPORTANT: Enable CORS for GitHub Pages
app.use(cors());

app.use(express.json());
app.use(express.static('public'));

// In-memory work area
let workArea = [];

// CSV file paths
const CSV_DIR = process.env.CSV_DIR || __dirname;
const FILES = {
  items: path.join(CSV_DIR, 'items_with_area.csv'),
  locations: path.join(CSV_DIR, 'locations_with_areas.csv'),
  inventory: path.join(CSV_DIR, 'inventory.csv'),
  alerts: path.join(CSV_DIR, 'alerts-compvis.csv'),
};

// Helper: Read CSV
function readCSV(filePath) {
  return new Promise((resolve, reject) => {
    const results = [];
    if (!fs.existsSync(filePath)) {
      return resolve(results);
    }
    fs.createReadStream(filePath)
      .pipe(csv())
      .on('data', (data) => results.push(data))
      .on('end', () => resolve(results))
      .on('error', reject);
  });
}

// API: Get full status
app.get('/api/status', async (req, res) => {
  try {
    const items = await readCSV(FILES.items);
    const locations = await readCSV(FILES.locations);
    const alerts = await readCSV(FILES.alerts);

    // Update work area timers
    const now = Date.now();
    workArea.forEach(item => {
      const elapsed = Math.floor((now - item.checkedOutAt) / 1000);
      item.minutesElapsed = Math.floor(elapsed / 60);
      item.secondsLeft = Math.max(0, 900 - elapsed);
      item.overdue = elapsed > 900;
    });

    // Calculate location capacities
    // CSV headers: "Location", "Area (ft^2)"
    const locCapacity = locations.map(loc => {
      const locName = loc['Location'] || loc['location'] || '';
      const totalArea = parseFloat(loc['Area (ft^2)'] || loc['area'] || 0);
      const usedItems = items.filter(i => (i['location'] || i['Location'] || '') === locName);
      const usedArea = usedItems.reduce((sum, i) => sum + (parseFloat(i['Area (ft^2)'] || i['area'] || 0)), 0);
      const pct = totalArea > 0 ? Math.round((usedArea / totalArea) * 100) : 0;
      return {
        location: locName,
        totalArea: totalArea.toFixed(2),
        usedArea: usedArea.toFixed(2),
        pct
      };
    });

    // Recent alerts (last 20)
    const recentAlerts = alerts.slice(-20).reverse();

    res.json({
      summary: {
        totalItems: items.length,
        totalLocations: locations.length,
        workAreaCount: workArea.length,
        recentAlertCount: recentAlerts.length,
      },
      workArea,
      recentAlerts,
      locations: locCapacity,
    });
  } catch (error) {
    console.error('Error in /api/status:', error);
    res.status(500).json({ error: 'Failed to fetch status' });
  }
});

// API: Search items
app.get('/api/items', async (req, res) => {
  try {
    const items = await readCSV(FILES.items);
    const search = (req.query.search || '').toLowerCase();
    
    // CSV headers: "PartNumber", "Description", "Area (ft^2)", "Price"
    let filtered = items;
    if (search) {
      filtered = items.filter(item => 
        (item['PartNumber'] || item['partNumber'] || '').toLowerCase().includes(search) ||
        (item['Description'] || item['description'] || '').toLowerCase().includes(search)
      );
    }

    // Mark items in work area
    const results = filtered.map(item => {
      const pn = item['PartNumber'] || item['partNumber'] || '';
      const inWork = workArea.find(w => w.partNumber === pn);
      const loc = item['location'] || item['Location'] || '';
      return {
        partNumber: pn,
        description: item['Description'] || item['description'] || '',
        area: item['Area (ft^2)'] || item['area'] || '',
        price: item['Price'] || item['price'] || '',
        location: loc,
        status: inWork ? 'WORK AREA' : (loc ? 'IN STORAGE' : 'UNASSIGNED')
      };
    });

    res.json({ items: results });
  } catch (error) {
    console.error('Error in /api/items:', error);
    res.status(500).json({ error: 'Failed to fetch items' });
  }
});

// API: Receive alert from CV script
app.post('/api/alert', (req, res) => {
  const { location, status } = req.body;
  if (!location || !status) {
    return res.status(400).json({ error: 'location and status required' });
  }
  const timestamp = new Date().toISOString().replace('T', ' ').substring(0, 23);
  const line = `${timestamp},${location},${status}\n`;
  fs.appendFile(FILES.alerts, line, err => {
    if (err) return res.status(500).json({ error: 'Failed to write alert' });
    res.json({ ok: true, timestamp, location, status });
  });
});

// API: Get all alerts
app.get('/api/alerts', async (req, res) => {
  try {
    const alerts = await readCSV(FILES.alerts);
    res.json({ alerts });
  } catch (error) {
    console.error('Error in /api/alerts:', error);
    res.status(500).json({ error: 'Failed to fetch alerts' });
  }
});

// API: Checkout item
app.post('/api/checkout', async (req, res) => {
  try {
    const { partNumber } = req.body;
    if (!partNumber) {
      return res.status(400).json({ error: 'Part number required' });
    }

    // Check if already checked out
    if (workArea.find(w => w.partNumber === partNumber)) {
      return res.status(400).json({ error: 'Already in work area' });
    }

    // Get item details
    const items = await readCSV(FILES.items);
    const item = items.find(i => (i['PartNumber'] || i['partNumber'] || '') === partNumber);
    if (!item) {
      return res.status(404).json({ error: 'Item not found' });
    }

    // Add to work area
    workArea.push({
      partNumber,
      description: item['Description'] || item['description'] || '',
      price: item['Price'] || item['price'] || '',
      checkedOutAt: Date.now(),
      minutesElapsed: 0,
      secondsLeft: 900,
      overdue: false,
    });

    res.json({ message: 'Checked out successfully', partNumber });
  } catch (error) {
    console.error('Error in /api/checkout:', error);
    res.status(500).json({ error: 'Failed to checkout item' });
  }
});

// API: Checkin item
app.post('/api/checkin', async (req, res) => {
  try {
    const { partNumber } = req.body;
    if (!partNumber) {
      return res.status(400).json({ error: 'Part number required' });
    }

    const itemIndex = workArea.findIndex(w => w.partNumber === partNumber);
    if (itemIndex === -1) {
      return res.status(404).json({ error: 'Item not in work area' });
    }

    const item = workArea[itemIndex];
    const elapsed = Math.floor((Date.now() - item.checkedOutAt) / 1000);
    const minutesElapsed = Math.floor(elapsed / 60);
    const overdue = elapsed > 900;

    workArea.splice(itemIndex, 1);

    res.json({
      message: 'Checked in successfully',
      partNumber,
      minutesElapsed,
      overdue,
    });
  } catch (error) {
    console.error('Error in /api/checkin:', error);
    res.status(500).json({ error: 'Failed to checkin item' });
  }
});

// Start server
app.listen(PORT, () => {
  console.log(`\n  TrackIT Warehouse Monitor running at http://localhost:${PORT}`);
  console.log(`  Watching CSVs in: ${CSV_DIR}\n`);
});
