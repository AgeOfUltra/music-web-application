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
}

const notifier = new ToastNotifier();

window.addEventListener('DOMContentLoaded', function() {
    const fieldErrorsContainer = document.getElementById('fieldErrors');
    const generalErrorContainer = document.getElementById('generalError');

    const errorItems = fieldErrorsContainer.querySelectorAll('.error-item');
    errorItems.forEach(item => {
        const errorMessage = item.textContent.trim();
        if (errorMessage) {
            notifier.error(errorMessage);
        }
    });

    const generalError = generalErrorContainer.textContent.trim();
    if (generalError) {
        notifier.error(generalError);
    }

    if (errorItems.length > 0 || generalError) {
        document.getElementById('password').value = '';
        document.getElementById('username').value = '';
        document.getElementById('email').value = '';
    }
});