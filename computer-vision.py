# ========================================
# WAREHOUSE OBJECT MONITORING SYSTEM
# ========================================
# Monitors a specific location via webcam to detect if an object goes missing
# Compares current video feed against a baseline "empty" image

import cv2  # Computer vision and webcam access
import numpy as np
import csv
from datetime import datetime
import time
import os
import requests  # pip install requests

# TrackIT server — must be running locally via: node server.js
SERVER_URL = 'https://autoflagger.onrender.com'

# ========================================
# CONFIGURATION
# ========================================

cap = cv2.VideoCapture(0)

location_name = 'Storage Location 1'
location_coords = (150, 100, 500, 450)  # (x1, y1, x2, y2) - rectangular monitoring area

baseline_empty = None  # Stores what the "empty" location looks like
mode = "setup"

# Alert cooldown system - prevents spam alerts
last_alert_time = 0
was_missing = False
ALERT_COOLDOWN = 90  # Minimum seconds between alerts
STABILIZATION_DELAY = 2  # Seconds the state must remain stable before alerting

# Stabilization state
last_change_time = 0
stable_state = None

# ========================================
# FUNCTIONS
# ========================================

def capture_baseline(frame, coords):
    """Captures a reference image of the EMPTY storage location"""
    x1, y1, x2, y2 = coords
    roi = frame[y1:y2, x1:x2]
    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    blurred = cv2.GaussianBlur(gray, (25, 25), 0)  # Blur reduces noise
    return blurred

def is_object_present(current_frame, empty_baseline):
    """Compares current frame to baseline to determine if an object is present"""
    if empty_baseline is None:
        return False
    
    # Calculate difference between current frame and empty baseline
    diff = cv2.absdiff(current_frame, empty_baseline)
    _, binary = cv2.threshold(diff, 30, 255, cv2.THRESH_BINARY)
    contours, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    
    # Calculate total area of detected changes
    total_area = sum(cv2.contourArea(c) for c in contours)
    min_area = 50000  # Threshold to ignore small movements/shadows
    
    print(f"Detected area: {total_area:.0f} pixels")
    
    return total_area > min_area

def write_alert():
    """POSTs a MISSING alert to the TrackIT server (with cooldown to prevent spam)"""
    global last_alert_time
    current_time = time.time()

    if (current_time - last_alert_time) > ALERT_COOLDOWN:
        try:
            res = requests.post(
                f"{SERVER_URL}/api/alert",
                json={"location": location_name, "status": "MISSING"},
                timeout=3
            )
            if res.ok:
                print(f"Alert sent to server for {location_name}")
            else:
                print(f"Server rejected alert: {res.text}")
        except Exception as e:
            print(f"Could not reach server, writing to CSV directly: {e}")
            with open("alerts-compvis.csv", "a", newline="") as f:
                import csv as _csv
                _csv.writer(f).writerow([datetime.now(), location_name, "MISSING"])
        last_alert_time = current_time

def reset_cooldown():
    """Resets alert system when object returns"""
    global last_alert_time, was_missing
    last_alert_time = 0
    was_missing = False

def clear_alerts():
    """Deletes the alerts CSV file"""
    if os.path.exists('alerts-compvis.csv'):
        os.remove('alerts-compvis.csv')
        print("All alerts cleared!")
    else:
        print("No alerts file to clear")

# ========================================
# MAIN MONITORING LOOP
# ========================================

while True:
    ret, frame = cap.read()
    
    if not ret:
        break
    
    x1, y1, x2, y2 = location_coords
    
    # SETUP MODE - Waiting for baseline capture
    if mode == "setup":
        color = (255, 255, 0)
        cv2.rectangle(frame, (x1, y1), (x2, y2), color, 3)
        cv2.putText(frame, "Press C to capture EMPTY box baseline", (x1, y1-10), 
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2)
    
    # MONITOR MODE - Actively checking for object
    elif mode == "monitor":
        roi = frame[y1:y2, x1:x2]
        gray_roi = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
        blurred_roi = cv2.GaussianBlur(gray_roi, (25, 25), 0)
        
        if baseline_empty is not None:
            current_time = time.time()
            object_present = is_object_present(blurred_roi, baseline_empty)
            
            # Track state changes and start stabilization timer
            if stable_state != object_present:
                last_change_time = current_time
                stable_state = object_present
                print("State change detected - stabilizing...")
            
            time_since_change = current_time - last_change_time
            
            if time_since_change >= STABILIZATION_DELAY:
                if not object_present:
                    color = (0, 0, 255)  # Red
                    status = "ALERT: MISSING"
                    write_alert()
                    was_missing = True
                else:
                    color = (0, 255, 0)  # Green
                    status = "OK"
                    if was_missing:
                        reset_cooldown()
                        print("Object returned - cooldown reset")
            else:
                color = (255, 165, 0)  # Orange
                status = f"STABILIZING ({int(STABILIZATION_DELAY - time_since_change)}s)"
            
            cv2.rectangle(frame, (x1, y1), (x2, y2), color, 3)
            cv2.putText(frame, f"{location_name}: {status}", (x1, y1-10), 
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 2)
    
    # Display info overlay
    cv2.putText(frame, f"Mode: {mode.upper()}", (10, 30), 
                cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 2)
    cv2.putText(frame, "C=capture empty | M=monitor | R=clear | S=setup | Q=quit", (10, 60), 
                cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 255, 255), 1)
    
    cv2.imshow('Warehouse Monitor', frame)
    
    # ========================================
    # KEYBOARD INPUT HANDLING
    # ========================================
    
    key = cv2.waitKey(1) & 0xFF
    
    if key == ord('c'):
        baseline_empty = capture_baseline(frame, location_coords)
        print("Empty baseline captured! Add object and press M to monitor")
    
    elif key == ord('m'):
        if baseline_empty is not None:
            mode = "monitor"
            stable_state = None
            last_change_time = time.time()
            print("Monitoring started!")
        else:
            print("Capture empty baseline first (press C with EMPTY box)!")
    
    elif key == ord('s'):
        mode = "setup"
        baseline_empty = None
        stable_state = None
        print("Back to setup mode")
    
    elif key == ord('r'):
        clear_alerts()
    
    elif key == 27 or key == ord('q'):
        break

# Cleanup
cap.release()
cv2.destroyAllWindows()
