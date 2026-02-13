
// ============================================
// INTERNET SPEED MONITOR (OPTIMIZED)
// ============================================

class InternetSpeedMonitor {
    constructor() {
        this.isExpanded = false;
        this.connectionQuality = 'checking';
        this.downloadSpeed = 0;
        this.latency = 0;
        this.autoHideTimer = null;
        this.wasOffline = false; // Track previous offline state

        this.init();
    }

    init() {
        this.createWidget();
        this.startMonitoring();
        this.attachEventListeners();
    }

    createWidget() {
        const widgetHTML = `
            <div id="internetSpeedWidget" class="internet-speed-widget">
                <div class="speed-indicator" id="speedIndicator">
                    <div class="speed-icon">
                        <i class="bi bi-wifi"></i>
                    </div>
                    <div class="speed-status" id="speedStatus">
                        <span class="status-dot"></span>
                    </div>
                </div>
                <div class="speed-details" id="speedDetails">
                    <div class="speed-header">
                        <i class="bi bi-wifi me-2"></i>
                        <span>Connection Status</span>
                    </div>
                    <div class="speed-info">
                        <div class="info-row">
                            <span class="info-label">Quality:</span>
                            <span class="info-value" id="qualityValue">Checking...</span>
                        </div>
                        <div class="info-row">
                            <span class="info-label">Speed:</span>
                            <span class="info-value" id="speedValue">-- Mbps</span>
                        </div>
                        <div class="info-row">
                            <span class="info-label">Latency:</span>
                            <span class="info-value" id="latencyValue">-- ms</span>
                        </div>
                    </div>
                </div>
            </div>

            <style>
                .internet-speed-widget {
                    position: fixed;
                    right: 20px;
                    top: 50%;
                    transform: translateY(-50%);
                    z-index: 9999;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                }

                .speed-indicator {
                    width: 50px;
                    height: 50px;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    cursor: pointer;
                    box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
                    transition: all 0.3s ease;
                    position: relative;
                }

                .speed-indicator:hover {
                    transform: scale(1.1);
                    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.3);
                }

                .speed-icon {
                    color: white;
                    font-size: 24px;
                }

                .speed-status {
                    position: absolute;
                    bottom: 2px;
                    right: 2px;
                }

                .status-dot {
                    width: 12px;
                    height: 12px;
                    border-radius: 50%;
                    background: #4ade80;
                    display: inline-block;
                    border: 2px solid white;
                    animation: pulse 2s infinite;
                }

                @keyframes pulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.5; }
                }

                .speed-details {
                    position: absolute;
                    right: 60px;
                    top: 0;
                    background: white;
                    border-radius: 12px;
                    padding: 16px;
                    min-width: 250px;
                    box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
                    opacity: 0;
                    visibility: hidden;
                    transform: translateX(20px);
                    transition: all 0.3s ease;
                }

                .speed-details.show {
                    opacity: 1;
                    visibility: visible;
                    transform: translateX(0);
                }

                .speed-header {
                    font-weight: 600;
                    font-size: 16px;
                    color: #1f2937;
                    margin-bottom: 12px;
                    display: flex;
                    align-items: center;
                }

                .speed-info {
                    display: flex;
                    flex-direction: column;
                    gap: 10px;
                }

                .info-row {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                }

                .info-label {
                    color: #6b7280;
                    font-size: 14px;
                }

                .info-value {
                    color: #1f2937;
                    font-weight: 600;
                    font-size: 14px;
                }

                /* Connection quality colors */
                .quality-excellent { color: #10b981 !important; }
                .quality-good { color: #3b82f6 !important; }
                .quality-fair { color: #f59e0b !important; }
                .quality-poor { color: #ef4444 !important; }
                .quality-offline { color: #6b7280 !important; }

                /* Status dot colors */
                .status-excellent { background: #10b981 !important; }
                .status-good { background: #3b82f6 !important; }
                .status-fair { background: #f59e0b !important; }
                .status-poor { background: #ef4444 !important; }
                .status-offline { background: #6b7280 !important; animation: none; }
            </style>
        `;

        // Insert into the dedicated container
        const container = document.getElementById('internetWidgetContainer');
        if (container) {
            container.innerHTML = widgetHTML;
        } else {
            // Fallback to body if container not found
            document.body.insertAdjacentHTML('beforeend', widgetHTML);
        }
    }

    attachEventListeners() {
        const indicator = document.getElementById('speedIndicator');

        indicator.addEventListener('click', () => {
            this.toggleDetails();
        });

        // Close when clicking outside
        document.addEventListener('click', (e) => {
            const widget = document.getElementById('internetSpeedWidget');
            if (!widget.contains(e.target) && this.isExpanded) {
                this.hideDetails();
            }
        });
    }

    toggleDetails() {
        if (this.isExpanded) {
            this.hideDetails();
        } else {
            this.showDetails();
        }
    }

    showDetails() {
        const details = document.getElementById('speedDetails');
        details.classList.add('show');
        this.isExpanded = true;

        // Auto-hide after 5 seconds
        this.clearAutoHideTimer();
        this.autoHideTimer = setTimeout(() => {
            this.hideDetails();
        }, 5000);
    }

    hideDetails() {
        const details = document.getElementById('speedDetails');
        details.classList.remove('show');
        this.isExpanded = false;
        this.clearAutoHideTimer();
    }

    clearAutoHideTimer() {
        if (this.autoHideTimer) {
            clearTimeout(this.autoHideTimer);
            this.autoHideTimer = null;
        }
    }

    async startMonitoring() {
        // Initial check
        await this.checkConnection();

        // Monitor online/offline events with notifications
        window.addEventListener('online', () => {
            console.log('🌐 Connection restored');
            notifier.success('Internet connection restored!', 3000);
            this.checkConnection();
        });

        window.addEventListener('offline', () => {
            console.log('📡 Connection lost');
            notifier.error('Internet connection lost!', 4000);
            this.updateStatus('offline', 0, 0);
        });

        // Periodic checks every 20 seconds (optimized from 30)
        setInterval(() => {
            if (navigator.onLine) {
                this.checkConnection();
            }
        }, 20000);
    }

    async checkConnection() {
        if (!navigator.onLine) {
            this.updateStatus('offline', 0, 0);
            return;
        }

        try {
            const startTime = performance.now();

            // Ping test using a small resource
            const response = await fetch('/public/internal/speed/test?ts=' + Date.now(), {
                method: 'GET',
                cache: 'no-cache'
            });

            const endTime = performance.now();
            const latency = Math.round(endTime - startTime);

            // Estimate speed based on latency and connection type
            const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
            let estimatedSpeed = 0;

            if (connection && connection.downlink) {
                estimatedSpeed = connection.downlink;
            } else {
                // Estimate based on latency
                if (latency < 50) estimatedSpeed = 50;
                else if (latency < 100) estimatedSpeed = 25;
                else if (latency < 200) estimatedSpeed = 10;
                else if (latency < 500) estimatedSpeed = 5;
                else estimatedSpeed = 1;
            }

            // Determine quality
            let quality;
            if (latency < 50 && estimatedSpeed > 20) quality = 'excellent';
            else if (latency < 100 && estimatedSpeed > 10) quality = 'good';
            else if (latency < 200 && estimatedSpeed > 5) quality = 'fair';
            else quality = 'poor';

            this.updateStatus(quality, estimatedSpeed, latency);

            // If was offline and now online, show reconnection message
            if (this.wasOffline) {
                notifier.success('Connection quality: ' + quality.charAt(0).toUpperCase() + quality.slice(1), 3000);
                this.wasOffline = false;
            }

        } catch (error) {
            console.error('Connection check failed:', error);
            this.updateStatus('poor', 0, 999);
        }
    }

    updateStatus(quality, speed, latency) {
        this.connectionQuality = quality;
        this.downloadSpeed = speed;
        this.latency = latency;

        // Track offline state
        if (quality === 'offline') {
            this.wasOffline = true;
        }

        const statusDot = document.querySelector('.status-dot');
        const qualityValue = document.getElementById('qualityValue');
        const speedValue = document.getElementById('speedValue');
        const latencyValue = document.getElementById('latencyValue');

        if (!statusDot || !qualityValue || !speedValue || !latencyValue) return;

        // Update status dot color
        statusDot.className = 'status-dot status-' + quality;

        // Update quality text
        const qualityText = {
            'excellent': 'Excellent',
            'good': 'Good',
            'fair': 'Fair',
            'poor': 'Poor',
            'offline': 'Offline',
            'checking': 'Checking...'
        };

        qualityValue.textContent = qualityText[quality] || 'Unknown';
        qualityValue.className = 'info-value quality-' + quality;

        // Update speed
        if (quality === 'offline') {
            speedValue.textContent = 'No Connection';
            latencyValue.textContent = '--';
        } else {
            speedValue.textContent = speed > 0 ? `~${speed.toFixed(1)} Mbps` : 'Checking...';
            latencyValue.textContent = latency > 0 ? `${latency} ms` : '--';
        }
    }
}

// Function to show custom error toast
function showErrorToast(message) {
    const toastContainer = document.body;
    const toastHTML = `
        <div class="toast-notification error-toast" style="display: flex;">
            <div class="toast-content">
                <i class="bi bi-exclamation-circle"></i>
                <span>${message}</span>
            </div>
        </div>
    `;

    const toastElement = document.createElement('div');
    toastElement.innerHTML = toastHTML;
    const toast = toastElement.querySelector('.toast-notification');
    toastContainer.appendChild(toast);

    setTimeout(() => {
        toast.style.animation = 'slideOutRight 0.4s ease forwards';
        setTimeout(() => {
            toast.remove();
        }, 400);
    }, 3000);
}

// File upload handling
const fileInput = document.getElementById('fileInput');
const fileNameDisplay = document.getElementById('fileNameDisplay');
const uploadPlaceholder = document.querySelector('.upload-placeholder');
const uploadForm = document.getElementById('uploadForm');

// Handle file selection
fileInput.addEventListener('change', function(e) {
    const file = e.target.files[0];
    if (file) {
        displayFileName(file.name, file.size);
    }
});



// Handle drag and drop
uploadPlaceholder.addEventListener('dragover', (e) => {
    e.preventDefault();
    uploadPlaceholder.style.borderColor = '#764ba2';
    uploadPlaceholder.style.backgroundColor = '#f0f2ff';
});

uploadPlaceholder.addEventListener('dragleave', () => {
    uploadPlaceholder.style.borderColor = '#667eea';
    uploadPlaceholder.style.backgroundColor = '#f8f9ff';
});

uploadPlaceholder.addEventListener('drop', (e) => {
    e.preventDefault();
    uploadPlaceholder.style.borderColor = '#667eea';
    uploadPlaceholder.style.backgroundColor = '#f8f9ff';

    const files = e.dataTransfer.files;
    if (files.length > 0) {
        fileInput.files = files;
        displayFileName(files[0].name, files[0].size);
    }
});

function displayFileName(name, size) {
    const sizeMB = (size / (1024 * 1024)).toFixed(2);
    fileNameDisplay.innerHTML = `<i class="bi bi-check-circle"></i> ${name} (${sizeMB} MB)`;
    fileNameDisplay.classList.add('active');
}

// Handle toast notifications
document.addEventListener('DOMContentLoaded', function() {
    const successToast = document.getElementById('successToast');
    const errorToast = document.getElementById('errorToast');

    new InternetSpeedMonitor();
    if (successToast) {
        showToast(successToast);
        setTimeout(() => {
            clearFormFields();
        }, 500);
    }

    if (errorToast) {
        showToast(errorToast);
    }

    function showToast(toastElement) {
        toastElement.style.display = 'flex';
        setTimeout(() => {
            toastElement.style.animation = 'slideOutRight 0.4s ease forwards';
            setTimeout(() => {
                toastElement.style.display = 'none';
            }, 400);
        }, 3000);
    }

    function clearFormFields() {
        document.getElementById('fileInput').value = '';
        document.getElementById('songName').value = '';
        document.getElementById('fileName').value = '';
        document.getElementById('movie').value = '';
        document.getElementById('singer').value = '';
        document.getElementById('language').value = '';
        document.getElementById('songType').value = '';
        document.getElementById('hero').value = '';
        document.getElementById('heroine').value = '';
        document.getElementById('fileNameDisplay').textContent = '';
        document.getElementById('fileNameDisplay').classList.remove('active');
    }
});

// Add slide out animation styles if not already present
const style = document.createElement('style');
style.textContent = `
    @keyframes slideOutRight {
        from {
            opacity: 1;
            transform: translateX(0);
        }
        to {
            opacity: 0;
            transform: translateX(400px);
        }
    }
`;
document.head.appendChild(style);