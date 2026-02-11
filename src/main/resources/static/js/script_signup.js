
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

// ============================================
// TOAST NOTIFIER CLASS
// ============================================

class ToastNotifier {
    constructor() {
        this.container = document.getElementById('toastContainer');
    }

    show(message, type = 'error', duration = 5000) {
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
}

const notifier = new ToastNotifier();

// ============================================
// PASSWORD VISIBILITY TOGGLE
// ============================================

(function setupPasswordToggle() {
    const togglePassword = document.getElementById('togglePassword');
    const passwordField = document.getElementById('password');
    const toggleIcon = document.getElementById('toggleIcon');

    if (togglePassword && passwordField && toggleIcon) {
        togglePassword.addEventListener('click', function() {
            const type = passwordField.type === 'password' ? 'text' : 'password';
            passwordField.type = type;

            if (type === 'password') {
                toggleIcon.classList.remove('bi-eye-slash');
                toggleIcon.classList.add('bi-eye');
            } else {
                toggleIcon.classList.remove('bi-eye');
                toggleIcon.classList.add('bi-eye-slash');
            }
        });
    }
})();

// ============================================
// USERNAME VALIDATION
// ============================================

(function setupUsernameValidation() {
    const usernameInput = document.getElementById('username');
    const errorMessage = document.getElementById('usernameError');

    if (!usernameInput || !errorMessage) return;

    // Allowed special characters: @ $ & #
    function validateUsername(username) {
        // Check for minimum length
        if (username.length > 0 && username.length < 5) {
            return { valid: false, message: 'Username must be at least 5 characters' };
        }

        // Check for spaces
        if (/\s/.test(username)) {
            return { valid: false, message: 'Username cannot contain spaces' };
        }

        // Check for disallowed special characters
        const specialCharRegex = /[^a-zA-Z0-9@$&#]/g;
        const invalidChars = username.match(specialCharRegex);

        if (invalidChars) {
            const uniqueInvalidChars = [...new Set(invalidChars)].join(', ');
            return {
                valid: false,
                message: `Invalid characters: ${uniqueInvalidChars}. Only @ $ & # are allowed.`
            };
        }

        // All validations passed
        if (username.length >= 5) {
            return { valid: true, message: '' };
        }

        return { valid: false, message: '' };
    }

    // Prevent spaces from being typed
    usernameInput.addEventListener('keydown', function(e) {
        if (e.key === ' ' || e.keyCode === 32) {
            e.preventDefault();
            errorMessage.textContent = 'Spaces are not allowed';
            errorMessage.style.display = 'block';
            usernameInput.classList.add('invalid');

            setTimeout(() => {
                if (usernameInput.value.length >= 5) {
                    errorMessage.style.display = 'none';
                    usernameInput.classList.remove('invalid');
                }
            }, 2000);
        }
    });

    // Validate on input
    usernameInput.addEventListener('input', function() {
        const result = validateUsername(this.value);

        if (!result.valid) {
            // Remove invalid characters (spaces and disallowed special chars)
            this.value = this.value.replace(/\s/g, '').replace(/[^a-zA-Z0-9@$&#]/g, '');

            if (result.message) {
                errorMessage.textContent = result.message;
                errorMessage.style.display = 'block';
            }
            this.classList.add('invalid');
            this.classList.remove('valid');
        } else if (this.value.length >= 5) {
            errorMessage.style.display = 'none';
            this.classList.remove('invalid');
            this.classList.add('valid');
        } else {
            // Still typing, not at 5 characters yet
            this.classList.remove('invalid', 'valid');
            if (this.value.length > 0) {
                errorMessage.textContent = 'Username must be at least 5 characters';
                errorMessage.style.display = 'block';
            } else {
                errorMessage.style.display = 'none';
            }
        }
    });

    // Validate on blur
    usernameInput.addEventListener('blur', function() {
        const result = validateUsername(this.value);

        if (!result.valid && this.value.length > 0) {
            errorMessage.textContent = result.message;
            errorMessage.style.display = 'block';
            this.classList.add('invalid');
        } else if (this.value.length === 0) {
            errorMessage.style.display = 'none';
            this.classList.remove('invalid', 'valid');
        }
    });
})();

// ============================================
// PASSWORD VALIDATION
// ============================================

(function setupPasswordValidation() {
    const passwordInput = document.getElementById('password');
    const errorMessage = document.getElementById('passwordError');

    // Requirement elements
    const reqLength = document.getElementById('req-length');
    const reqUppercase = document.getElementById('req-uppercase');
    const reqLowercase = document.getElementById('req-lowercase');
    const reqNumber = document.getElementById('req-number');
    const reqSpecial = document.getElementById('req-special');

    if (!passwordInput) return;

    function validatePassword(password) {
        const checks = {
            length: password.length >= 8 && password.length <= 20,
            uppercase: /[A-Z]/.test(password),
            lowercase: /[a-z]/.test(password),
            number: /[0-9]/.test(password),
            special: /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password)
        };

        // Update visual indicators
        if (reqLength) {
            reqLength.className = checks.length ? 'valid' : 'invalid';
        }
        if (reqUppercase) {
            reqUppercase.className = checks.uppercase ? 'valid' : 'invalid';
        }
        if (reqLowercase) {
            reqLowercase.className = checks.lowercase ? 'valid' : 'invalid';
        }
        if (reqNumber) {
            reqNumber.className = checks.number ? 'valid' : 'invalid';
        }
        if (reqSpecial) {
            reqSpecial.className = checks.special ? 'valid' : 'invalid';
        }

        const allValid = Object.values(checks).every(check => check === true);
        return { valid: allValid, checks };
    }

    // Validate on input
    passwordInput.addEventListener('input', function() {
        const result = validatePassword(this.value);

        if (!result.valid && this.value.length > 0) {
            this.classList.add('invalid');
            this.classList.remove('valid');

            // Show specific error message
            let errorMsg = 'Password must contain: ';
            const missing = [];

            if (!result.checks.length) missing.push('8-20 characters');
            if (!result.checks.uppercase) missing.push('uppercase letter');
            if (!result.checks.lowercase) missing.push('lowercase letter');
            if (!result.checks.number) missing.push('number');
            if (!result.checks.special) missing.push('special character');

            if (errorMessage && missing.length > 0) {
                errorMessage.textContent = errorMsg + missing.join(', ');
                errorMessage.style.display = 'block';
            }
        } else if (result.valid) {
            this.classList.remove('invalid');
            this.classList.add('valid');
            if (errorMessage) {
                errorMessage.style.display = 'none';
            }
        } else {
            this.classList.remove('invalid', 'valid');
            if (errorMessage) {
                errorMessage.style.display = 'none';
            }
        }
    });

    // Validate on blur
    passwordInput.addEventListener('blur', function() {
        const result = validatePassword(this.value);

        if (!result.valid && this.value.length > 0) {
            this.classList.add('invalid');
            if (errorMessage) {
                errorMessage.textContent = 'Password does not meet requirements';
                errorMessage.style.display = 'block';
            }
        }
    });
})();

// ============================================
// FORM SUBMISSION VALIDATION
// ============================================

(function setupFormValidation() {
    const form = document.getElementById('signupFormElement');

    if (!form) return;

    form.addEventListener('submit', function(e) {
        const usernameInput = document.getElementById('username');
        const passwordInput = document.getElementById('password');
        const emailInput = document.getElementById('email');

        let isValid = true;

        // Validate username - must be at least 5 characters and contain only allowed characters
        const usernameRegex = /^[a-zA-Z0-9@$&#]+$/;
        const usernameValue = usernameInput.value.trim();

        if (usernameValue.length < 5) {
            e.preventDefault();
            isValid = false;
            notifier.error('Username must be at least 5 characters');
            usernameInput.classList.add('invalid');
        } else if (!usernameRegex.test(usernameValue) || /\s/.test(usernameValue)) {
            e.preventDefault();
            isValid = false;
            notifier.error('Username can only contain letters, numbers, and @ $ & # characters');
            usernameInput.classList.add('invalid');
        }

        // Validate password
        const passwordRegex = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]).*$/;
        const passwordLength = passwordInput.value.length >= 8 && passwordInput.value.length <= 20;

        if (!passwordRegex.test(passwordInput.value) || !passwordLength) {
            e.preventDefault();
            isValid = false;
            notifier.error('Password must be 8-20 characters with uppercase, lowercase, number, and special character');
            passwordInput.classList.add('invalid');
        }

        // Validate email
        if (!emailInput.validity.valid) {
            e.preventDefault();
            isValid = false;
            notifier.error('Please enter a valid email address');
            emailInput.classList.add('invalid');
        }

        if (!isValid) {
            console.log('❌ Form validation failed');
        }
    });
})();

// ============================================
// HANDLE SERVER-SIDE ERRORS
// ============================================

window.addEventListener('DOMContentLoaded', function() {
    const fieldErrorsContainer = document.getElementById('fieldErrors');
    const generalErrorContainer = document.getElementById('generalError');

    // Show field errors
    const errorItems = fieldErrorsContainer.querySelectorAll('.error-item');
    errorItems.forEach(item => {
        const errorMessage = item.textContent.trim();
        if (errorMessage) {
            notifier.error(errorMessage);
        }
    });

    // Show general error
    const generalError = generalErrorContainer.textContent.trim();
    if (generalError) {
        notifier.error(generalError);
    }

    // IMPORTANT: Only clear fields if there are errors
    // DO NOT clear on successful submission
    if (errorItems.length > 0 || generalError) {
        console.log('⚠️ Server-side errors detected - clearing sensitive fields');

        // Clear password field (security)
        const passwordField = document.getElementById('password');
        if (passwordField) {
            passwordField.value = '';
        }

    }
});