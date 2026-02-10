import java.util.*;
import java.io.*;

public class WWT7
{
    static final int MAX_ITEMS = 1000;
    static final int MAX_LOCATIONS = 50;
    static final long FIFTEEN_MINUTES = 900000; // milliseconds

    static class Item
    {
        String partNumber;
        String description;
        double area;
        int price;
        int locationIndex;
        long workAreaCheckoutTime;

        Item(String partNumber, String description, double area, int price)
        {
            this.partNumber = partNumber;
            this.description = description;
            this.area = area;
            this.price = price;
            this.locationIndex = -1;
            this.workAreaCheckoutTime = 0;
        }

        boolean isInWorkArea()
        {
            return workAreaCheckoutTime > 0;
        }
    }

    public static void main(String[] args)
    {
        try
        {
            // ---------- LOAD LOCATIONS ----------
            List<String> locations = new ArrayList<>();
            List<Double> remainingArea = new ArrayList<>();

            Scanner locScanner = new Scanner(new File("locations_with_areas.csv"));
            locScanner.nextLine(); // skip header

            while (locScanner.hasNextLine())
            {
                String line = locScanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 2) continue;

                String name = parts[0].trim();
                String areaStr = parts[1].trim();

                if (name.isEmpty() || areaStr.isEmpty()) continue;

                locations.add(name);
                remainingArea.add(Double.parseDouble(areaStr));
            }
            locScanner.close();

            // ---------- LOAD ITEMS ----------
            List<Item> items = new ArrayList<>();
            Scanner itemScanner = new Scanner(new File("items_with_area.csv"));
            itemScanner.nextLine(); // skip header

            while (itemScanner.hasNextLine())
            {
                String line = itemScanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 4) continue;

                String partNumber = parts[0].trim();
                String description = parts[1].trim();
                String areaStr = parts[2].trim();
                String priceStr = parts[3].trim();

                if (partNumber.isEmpty() || areaStr.isEmpty()) continue;

                items.add(new Item(partNumber, description, 
                    Double.parseDouble(areaStr), Integer.parseInt(priceStr)));
            }
            itemScanner.close();

            System.out.println("Items loaded: " + items.size());
            System.out.println("Locations loaded: " + locations.size());

            // ---------- SORT & AUTO-ASSIGN ----------------
            items.sort((a, b) -> Double.compare(b.area, a.area));

            for (Item item : items)
            {
                int locIndex = bestFitLocation(item.area, remainingArea);

                if (locIndex != -1)
                {
                    item.locationIndex = locIndex;
                    remainingArea.set(locIndex, remainingArea.get(locIndex) - item.area);
                }
            }

            // ---------- MENU SYSTEM ----------------
            Scanner input = new Scanner(System.in);
            boolean done = false;

            while (!done)
            {
                System.out.println("\n===== WWT TECHNOLOGY DISTRIBUTION HUB =====");
                System.out.println("1. View all items");
                System.out.println("2. Move an item");
                System.out.println("3. Check out to work area");
                System.out.println("4. Check in from work area");
                System.out.println("5. View work area status");
                System.out.println("6. Exit and save");
                System.out.print("Choose option: ");

                int choice = input.nextInt();

                if (choice == 1)
                {
                    viewAllItems(items, locations);
                }
                else if (choice == 2)
                {
                    moveItem(items, locations, remainingArea, input);
                }
                else if (choice == 3)
                {
                    checkOutToWorkArea(items, locations, input);
                }
                else if (choice == 4)
                {
                    checkInFromWorkArea(items, locations, remainingArea, input);
                }
                else if (choice == 5)
                {
                    viewWorkAreaStatus(items);
                }
                else if (choice == 6)
                {
                    done = true;
                }
                else
                {
                    System.out.println("Invalid choice!");
                }
            }

            // ---------- SAVE OUTPUT ----------------
            File outFile = new File("wwt7.csv");
            PrintWriter writer = new PrintWriter(outFile);
            writer.println("PartNumber,Description,Area,Price,Location,Status");

            for (Item item : items)
            {
                String status = item.isInWorkArea() ? "IN WORK AREA" : "IN STORAGE";
                String location = item.isInWorkArea() ? "WORK AREA" 
                    : (item.locationIndex != -1 ? locations.get(item.locationIndex) : "NOT STORED");
                
                writer.println(
                    item.partNumber + "," +
                    item.description + "," +
                    item.area + "," +
                    item.price + "," +
                    location + "," +
                    status
                );
            }

            writer.close();
            input.close();
            System.out.println("\nFile saved to wwt7.csv. Goodbye!");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    // ---------- VIEW ALL ITEMS ----------
    public static void viewAllItems(List<Item> items, List<String> locations)
    {
        System.out.println("\n----- ALL ITEMS -----");
        int count = 0;
        for (Item item : items)
        {
            String status = "";
            if (item.isInWorkArea())
            {
                status = " [IN WORK AREA]";
            }
            else if (item.locationIndex != -1)
            {
                status = " -> Location " + locations.get(item.locationIndex);
            }
            else
            {
                status = " -> NOT STORED";
            }
            
            System.out.println(item.partNumber + " - " + item.description + status);
            count++;
            
            if (count >= 20)
            {
                System.out.println("... (" + (items.size() - 20) + " more items)");
                break;
            }
        }
    }

    // ---------- MOVE ITEM ----------
    public static void moveItem(List<Item> items, List<String> locations, 
                                List<Double> remaining, Scanner input)
    {
        System.out.print("\nEnter part number to move: ");
        String partNumber = input.next();

        Item targetItem = null;
        for (Item item : items)
        {
            if (item.partNumber.equals(partNumber))
            {
                targetItem = item;
                break;
            }
        }

        if (targetItem == null)
        {
            System.out.println("ERROR: Part number not found!");
            return;
        }

        if (targetItem.isInWorkArea())
        {
            System.out.println("ERROR: Item is currently in work area! Check it in first.");
            return;
        }

        System.out.println("Item: " + targetItem.description);
        System.out.println("Price: $" + String.format("%,d", targetItem.price));
        if (targetItem.locationIndex != -1)
        {
            System.out.println("Current location: " + locations.get(targetItem.locationIndex));
        }
        else
        {
            System.out.println("Current location: NOT STORED");
        }

        System.out.print("Enter new location number (0-" + (locations.size()-1) + "): ");
        int newLoc = input.nextInt();

        if (newLoc < 0 || newLoc >= locations.size())
        {
            System.out.println("ERROR: Invalid location!");
            return;
        }

        if (remaining.get(newLoc) >= targetItem.area)
        {
            // Return space to old location
            if (targetItem.locationIndex != -1)
            {
                remaining.set(targetItem.locationIndex, 
                    remaining.get(targetItem.locationIndex) + targetItem.area);
            }

            // Assign new location
            targetItem.locationIndex = newLoc;
            remaining.set(newLoc, remaining.get(newLoc) - targetItem.area);
            System.out.println("Item moved to location " + locations.get(newLoc));
        }
        else
        {
            System.out.println("ERROR: Not enough space in that location!");
        }
    }

    // ---------- CHECK OUT TO WORK AREA ----------
    public static void checkOutToWorkArea(List<Item> items, List<String> locations, Scanner input)
    {
        System.out.print("\nEnter part number to check out: ");
        String partNumber = input.next();

        Item targetItem = null;
        for (Item item : items)
        {
            if (item.partNumber.equals(partNumber))
            {
                targetItem = item;
                break;
            }
        }

        if (targetItem == null)
        {
            System.out.println("ERROR: Part number not found!");
            return;
        }

        if (targetItem.isInWorkArea())
        {
            System.out.println("ERROR: Item is already in work area!");
            return;
        }

        // Start work area timer
        targetItem.workAreaCheckoutTime = System.currentTimeMillis();
        
        System.out.println("Item checked out: " + targetItem.description);
        System.out.println("Value: $" + String.format("%,d", targetItem.price));
        System.out.println("WARNING: Must check in within 15 minutes!");
    }

    // ---------- CHECK IN FROM WORK AREA ----------
    public static void checkInFromWorkArea(List<Item> items, List<String> locations, 
                                          List<Double> remaining, Scanner input)
    {
        System.out.print("\nEnter part number to check in: ");
        String partNumber = input.next();

        Item targetItem = null;
        for (Item item : items)
        {
            if (item.partNumber.equals(partNumber))
            {
                targetItem = item;
                break;
            }
        }

        if (targetItem == null)
        {
            System.out.println("ERROR: Part number not found!");
            return;
        }

        if (!targetItem.isInWorkArea())
        {
            System.out.println("ERROR: Item is not in work area!");
            return;
        }

        // Check time elapsed
        long elapsed = System.currentTimeMillis() - targetItem.workAreaCheckoutTime;
        long minutes = elapsed / 60000;

        if (elapsed > FIFTEEN_MINUTES)
        {
            System.out.println("WARNING: Item " + targetItem.description + " checked in LATE!");
            System.out.println("Time elapsed: " + minutes + " minutes (Limit: 15 minutes)");
        }
        else
        {
            System.out.println("Item " + targetItem.description + " checked in on time.");
            System.out.println("Time elapsed: " + minutes + " minutes");
        }

        // Reset timer
        targetItem.workAreaCheckoutTime = 0;
        System.out.println("Item returned to location " + locations.get(targetItem.locationIndex));
    }

    // ---------- VIEW WORK AREA STATUS ----------
    public static void viewWorkAreaStatus(List<Item> items)
    {
        System.out.println("\n----- WORK AREA STATUS -----");
        int workAreaCount = 0;
        int totalValue = 0;
        
        for (Item item : items)
        {
            if (item.isInWorkArea())
            {
                long elapsed = System.currentTimeMillis() - item.workAreaCheckoutTime;
                long minutes = elapsed / 60000;
                long remaining = 15 - minutes;
                
                String status = (remaining > 0) 
                    ? remaining + " min remaining" 
                    : "OVERDUE by " + Math.abs(remaining) + " min";
                
                System.out.println(item.partNumber + " - " + item.description + 
                    " ($" + String.format("%,d", item.price) + ") - " + status);
                workAreaCount++;
                totalValue += item.price;
            }
        }
        
        if (workAreaCount == 0)
        {
            System.out.println("Work area is empty.");
        }
        else
        {
            System.out.println("\nTotal items in work area: " + workAreaCount);
            System.out.println("Total value in work area: $" + String.format("%,d", totalValue));
        }
    }

    // ---------- BEST FIT ----------------
    public static int bestFitLocation(double itemArea, List<Double> remaining)
    {
        int bestIndex = -1;
        double smallestWaste = Double.MAX_VALUE;

        for (int i = 0; i < remaining.size(); i++)
        {
            if (remaining.get(i) >= itemArea)
            {
                double waste = remaining.get(i) - itemArea;
                if (waste < smallestWaste)
                {
                    smallestWaste = waste;
                    bestIndex = i;
                }
            }
        }
        return bestIndex;
    }
}