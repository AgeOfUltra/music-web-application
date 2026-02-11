// ============================================
// JWT TOKEN CLEANUP ON LOGIN PAGE
// ============================================

const DEBUG = false;

if (!DEBUG) {
    console.log = function() {};
}


(function cleanupAuthenticationData() {
    console.log('🧹 Starting authentication data cleanup...');

    // 1. Clear JWT token from localStorage
    const localStorageKeys = [
        'jwtToken',
        'jwt',
        'authToken',
        'token',
        'access_token',
        'musicRoomActiveTab', // Also clear duplicate tab detection
        'user',
        'username',
        'userSession'
    ];

    localStorageKeys.forEach(key => {
        if (localStorage.getItem(key)) {
            localStorage.removeItem(key);
            console.log(`✅ Removed localStorage: ${key}`);
        }
    });

    // 2. Clear JWT token from sessionStorage
    const sessionStorageKeys = [
        'jwtToken',
        'jwt',
        'authToken',
        'token',
        'access_token',
        'cleanExit',
        'user',
        'username',
        'userSession'
    ];

    sessionStorageKeys.forEach(key => {
        if (sessionStorage.getItem(key)) {
            sessionStorage.removeItem(key);
            console.log(`✅ Removed sessionStorage: ${key}`);
        }
    });

    // 3. Clear all cookies (if JWT is stored in cookies)
    const cookies = document.cookie.split(';');
    cookies.forEach(cookie => {
        const cookieName = cookie.split('=')[0].trim();

        // Clear the cookie for current domain
        document.cookie = `${cookieName}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;`;

        // Also try to clear for root domain
        const domain = window.location.hostname;
        document.cookie = `${cookieName}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/; domain=${domain};`;

        console.log(`✅ Cleared cookie: ${cookieName}`);
    });

    // 4. Clear browser cache headers (optional - prevents back button from showing cached data)
    if (window.history && window.history.replaceState) {
        window.history.replaceState(null, document.title, window.location.href);
    }

    // 5. Disable browser cache for this page
    if (window.performance && window.performance.navigation.type === 1) {
        console.log('🔄 Page was reloaded - ensuring clean state');
    }

    console.log('✅ Authentication data cleanup completed');
})();

// Toast Notifier Class
class ToastNotifier {
    constructor() {
        this.container = document.getElementById('toastContainer');
    }

    show(message, type = 'error', duration = 4000) {
        const toast = document.createElement('div');
        toast.className = `toast-custom toast-${type}`;

        const icon = type === 'success' ? 'bi-check-circle-fill' : 'bi-exclamation-circle-fill';

        toast.innerHTML = `
            <span class="toast-icon">
                <i class="bi ${icon}"></i>
            </span>
            <div class="toast-content">${message}</div>
            <button class="toast-close" aria-label="Close">×</button>
        `;

        this.container.appendChild(toast);

        toast.querySelector('.toast-close').addEventListener('click', () => {
            this.removeToast(toast);
        });

        if (duration > 0) {
            setTimeout(() => {
                this.removeToast(toast);
            }, duration);
        }

        return toast;
    }

    removeToast(toast) {
        toast.classList.add('removing');
        setTimeout(() => {
            toast.remove();
        }, 300);
    }

    success(message, duration = 4000) {
        return this.show(message, 'success', duration);
    }

    error(message, duration = 5000) {
        return this.show(message, 'error', duration);
    }

    warning(message, duration = 4000) {
        return this.show(message, 'warning', duration);
    }

    info(message, duration = 4000) {
        return this.show(message, 'info', duration);
    }
}

const notifier = new ToastNotifier();

// ============================================
// PREVENT BROWSER BACK AND FORWARD NAVIGATION
// ============================================

(function preventBrowserNavigation() {
    // Track if user is intentionally navigating away (e.g., submitting login form)
    let allowNavigation = false;

    console.log('🔒 Login page navigation prevention initialized');

    // Method 1: History manipulation to prevent back/forward navigation
    function setupHistoryPrevention() {
        // Replace current state
        window.history.replaceState(
            { page: 'login', preventNav: true },
            document.title,
            window.location.href
        );

        // Push a dummy state to create a barrier
        window.history.pushState(
            { page: 'login-barrier', preventNav: true },
            document.title,
            window.location.href
        );

        // Listen for popstate event (triggered when back/forward button is clicked)
        window.addEventListener('popstate', function(event) {
            if (!allowNavigation) {
                // Immediately push state forward to keep user on login page
                window.history.pushState(
                    { page: 'login-barrier', preventNav: true },
                    document.title,
                    window.location.href
                );

                // Also push an additional state to maintain the barrier
                window.history.pushState(
                    { page: 'login-current', preventNav: true },
                    document.title,
                    window.location.href
                );

                // Show toast notification instead of alert
                notifier.error('Navigation is disabled on the login page.', 4000);
                console.log('⛔ Back/Forward navigation blocked');
            }
        });
    }

    // Method 2: Prevent keyboard shortcuts for back/forward navigation
    function preventKeyboardNavigation() {
        document.addEventListener('keydown', function(e) {
            // Prevent Alt+Left (back), Alt+Right (forward)
            if (e.altKey && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
                if (!allowNavigation) {
                    e.preventDefault();
                    notifier.error('Keyboard navigation is disabled on the login page.', 3000);
                    console.log('⌨️ Keyboard navigation prevented');
                }
            }

            // Prevent Backspace navigation (when not in input field)
            if (e.key === 'Backspace' &&
                e.target.tagName !== 'INPUT' &&
                e.target.tagName !== 'TEXTAREA' &&
                !e.target.isContentEditable) {
                if (!allowNavigation) {
                    e.preventDefault();
                    console.log('⌫ Backspace navigation prevented');
                }
            }
        });
    }

    // Method 3: Monitor mouse buttons (for mouse back/forward buttons)
    function preventMouseNavigation() {
        document.addEventListener('mousedown', function(e) {
            // Mouse button 3 = back, Mouse button 4 = forward
            if ((e.button === 3 || e.button === 4) && !allowNavigation) {
                e.preventDefault();
                notifier.error('Mouse navigation is disabled on the login page.', 3000);
                console.log('🖱️ Mouse navigation prevented');
            }
        });
    }

    // Method 4: Allow navigation when form is submitted or specific links are clicked
    function setupNavigationWhitelist() {
        // Allow navigation when login form is submitted
        const loginForm = document.querySelector('form');
        if (loginForm) {
            loginForm.addEventListener('submit', function() {
                allowNavigation = true;
                console.log('✅ Form submitted - navigation allowed');
            });
        }

        // Allow navigation when clicking on legitimate links (registration, forgot password, etc.)
        document.addEventListener('click', function(e) {
            const link = e.target.closest('a');
            if (link && link.href) {
                // Check if it's an internal navigation link
                if (link.href.includes('/register') ||
                    link.href.includes('/forgot-password') ||
                    link.href.includes('/home') ||
                    link.classList.contains('allow-navigation')) {
                    allowNavigation = true;
                    console.log('✅ Legitimate link clicked - navigation allowed');
                }
            }
        });

        // Allow navigation when clicking buttons that redirect
        const allowedButtons = document.querySelectorAll('button[type="submit"], .btn-navigation');
        allowedButtons.forEach(button => {
            button.addEventListener('click', function() {
                allowNavigation = true;
                console.log('✅ Navigation button clicked - navigation allowed');
            });
        });
    }

    // Method 5: Prevent beforeunload (optional - shows confirmation dialog)
    function setupBeforeUnloadPrevention() {
        window.addEventListener('beforeunload', function(e) {
            // Only prevent if navigation is not explicitly allowed
            if (!allowNavigation) {
                // This will show browser's native confirmation dialog
                // Commented out by default - uncomment if you want this extra protection
                // e.preventDefault();
                // e.returnValue = '';
                // return '';
            }
        });
    }

    // Initialize all prevention methods
    setupHistoryPrevention();
    preventKeyboardNavigation();
    preventMouseNavigation();
    setupNavigationWhitelist();
    setupBeforeUnloadPrevention();

    console.log('✅ Login page navigation prevention active');
})();


// ============================================
// PASSWORD VISIBILITY TOGGLE
// ============================================

(function setupPasswordToggle() {
    const togglePassword = document.getElementById('togglePassword');
    const passwordField = document.getElementById('password');
    const toggleIcon = document.getElementById('toggleIcon');

    if (togglePassword && passwordField && toggleIcon) {
        togglePassword.addEventListener('click', function() {
            // Toggle password visibility
            const type = passwordField.type === 'password' ? 'text' : 'password';
            passwordField.type = type;

            // Toggle icon
            if (type === 'password') {
                toggleIcon.classList.remove('bi-eye-slash');
                toggleIcon.classList.add('bi-eye');
            } else {
                toggleIcon.classList.remove('bi-eye');
                toggleIcon.classList.add('bi-eye-slash');
            }

            console.log('👁️ Password visibility toggled');
        });

        console.log('✅ Password toggle initialized');
    }
})();

// ============================================
// DISABLE BROWSER CACHE FOR LOGIN PAGE
// ============================================

(function disableBrowserCache() {
    // Set cache control headers via meta tags (if not already set by server)
    const existingCacheMeta = document.querySelector('meta[http-equiv="Cache-Control"]');

    if (!existingCacheMeta) {
        const meta1 = document.createElement('meta');
        meta1.httpEquiv = 'Cache-Control';
        meta1.content = 'no-cache, no-store, must-revalidate';
        document.head.appendChild(meta1);

        const meta2 = document.createElement('meta');
        meta2.httpEquiv = 'Pragma';
        meta2.content = 'no-cache';
        document.head.appendChild(meta2);

        const meta3 = document.createElement('meta');
        meta3.httpEquiv = 'Expires';
        meta3.content = '0';
        document.head.appendChild(meta3);

        console.log('✅ Browser cache disabled for login page');
    }
})();

// ============================================
// CHECK FOR SESSION EXPIRY PARAMETER
// ============================================

(function checkSessionExpiry() {
    const urlParams = new URLSearchParams(window.location.search);
    const expired = urlParams.get('expired');

    if (expired === 'true') {
        notifier.warning('Your session has expired. Please login again.', 5000);

        // Clean up the URL (remove ?expired=true parameter)
        if (window.history && window.history.replaceState) {
            const cleanUrl = window.location.pathname;
            window.history.replaceState({}, document.title, cleanUrl);
        }
    }
})();

// ============================================
// ORIGINAL LOGIN PAGE FUNCTIONALITY
// ============================================

window.addEventListener('DOMContentLoaded', function () {

    // Show registration success message
    const registrationSuccessContainer = document.getElementById('registrationSuccess');
    const showSuccess = registrationSuccessContainer ? registrationSuccessContainer.textContent.trim() : '';

    if (showSuccess === 'true') {
        notifier.success('Validation link sent to email', 7000);
    }

    // Show error message
    const errorContainer = document.getElementById('errorMessage');
    const errorMessage = errorContainer ? errorContainer.textContent.trim() : '';

    if (errorMessage) {
        notifier.error(errorMessage);

        // Clear username and password fields on error
        const usernameField = document.getElementById('username');
        const passwordField = document.getElementById('password');

        if (usernameField) usernameField.value = '';
        if (passwordField) passwordField.value = '';
    }

    // ✅ Final check: Ensure no authentication data exists
    console.log('🔍 Final authentication check on DOMContentLoaded');

    if (localStorage.getItem('jwtToken') ||
        sessionStorage.getItem('jwtToken') ||
        document.cookie.includes('jwtToken')) {

        console.warn('⚠️ Found residual authentication data - cleaning up again');

        // Trigger cleanup again
        localStorage.removeItem('jwtToken');
        sessionStorage.removeItem('jwtToken');

        // Clear all cookies again
        document.cookie.split(';').forEach(cookie => {
            const cookieName = cookie.split('=')[0].trim();
            document.cookie = `${cookieName}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;`;
        });
    }
});