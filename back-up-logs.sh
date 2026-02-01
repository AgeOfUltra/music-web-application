#!/bin/bash
# ============================================
# Log Backup Script - Music Web Application
# Scheduled via cron on Oracle VM
# ============================================
# Setup:
#   chmod +x backup-logs.sh
#   crontab -e
#   0 * * * * /home/ubuntu/music-web-application/backup-logs.sh
# ============================================

BACKUP_DIR="/home/ubuntu/music-web-application/logs-backup"
APP_LOG_DIR="/home/ubuntu/music-web-application/logs"
TRANSFER_DIR="/home/ubuntu/logs"
ARCHIVE_DIR="/home/ubuntu/logs/archive"
DATE=$(date +%Y-%m-%d_%H-%M-%S)
LOG_FILE="$BACKUP_DIR/backup-status.log"

# Create all required folders if they don't exist
mkdir -p "$BACKUP_DIR"
mkdir -p "$TRANSFER_DIR"
mkdir -p "$ARCHIVE_DIR"

echo "[$DATE] Starting log backup..." >> "$LOG_FILE"

# ============================================
# Step 1: Backup Docker container logs
# ============================================
docker logs music-web-application > "$BACKUP_DIR/docker-music-app-$DATE.log" 2>&1
if [ $? -eq 0 ]; then
    echo "[$DATE] Docker logs backed up successfully." >> "$LOG_FILE"
else
    echo "[$DATE] ERROR: Docker logs backup failed." >> "$LOG_FILE"
fi

# ============================================
# Step 2: Backup application file logs
# ============================================
if [ -d "$APP_LOG_DIR" ]; then
    cp -r "$APP_LOG_DIR"/* "$BACKUP_DIR/" 2>/dev/null
    echo "[$DATE] Application logs backed up successfully." >> "$LOG_FILE"
else
    echo "[$DATE] WARNING: App log directory not found at $APP_LOG_DIR" >> "$LOG_FILE"
fi

# ============================================
# Step 3: Transfer logs older than 3 days
#         from logs-backup → /home/ubuntu/logs
# ============================================
find "$BACKUP_DIR" -mtime +3 \( -name "docker-music-app-*.log" -o -name "music-app-*.log" -o -name "music-app-*.log.gz" \) -exec mv {} "$TRANSFER_DIR/" \;
if [ $? -eq 0 ]; then
    echo "[$DATE] Logs older than 3 days transferred to $TRANSFER_DIR" >> "$LOG_FILE"
else
    echo "[$DATE] ERROR: Transfer to $TRANSFER_DIR failed." >> "$LOG_FILE"
fi

# ============================================
# Step 4: Archive logs older than 30 days
#         in /home/ubuntu/logs → compress into archive folder
# ============================================
ARCHIVE_FILES=$(find "$TRANSFER_DIR" -maxdepth 1 -mtime +30 \( -name "*.log" -o -name "*.log.gz" \))
if [ -n "$ARCHIVE_FILES" ]; then
    echo "$ARCHIVE_FILES" | tar -czf "$ARCHIVE_DIR/logs-archive-$DATE.tar.gz" -T -
    if [ $? -eq 0 ]; then
        # Only delete after successful archive
        echo "$ARCHIVE_FILES" | xargs rm -f
        echo "[$DATE] Logs older than 30 days archived successfully." >> "$LOG_FILE"
    else
        echo "[$DATE] ERROR: Archive creation failed. Files NOT deleted." >> "$LOG_FILE"
    fi
else
    echo "[$DATE] No logs older than 30 days to archive." >> "$LOG_FILE"
fi

# ============================================
# Step 5: Cleanup archive files older than 90 days
# ============================================
find "$ARCHIVE_DIR" -name "logs-archive-*.tar.gz" -mtime +90 -delete
echo "[$DATE] Cleanup complete. Archives older than 90 days removed." >> "$LOG_FILE"
echo "[$DATE] Backup finished." >> "$LOG_FILE"