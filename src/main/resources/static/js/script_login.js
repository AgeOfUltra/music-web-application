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
}


const notifier = new ToastNotifier();

window.addEventListener('DOMContentLoaded', function () {

    const registrationSuccessContainer = document.getElementById('registrationSuccess');
    const showSuccess = registrationSuccessContainer.textContent.trim();

    if (showSuccess === 'true') {
        notifier.success('Registration Successful! Please login now.', 5000);
    }
    const errorContainer = document.getElementById('errorMessage');
    const errorMessage = errorContainer.textContent.trim();


    if (errorMessage) {
        notifier.error(errorMessage);

        document.getElementById('username').value = '';
        document.getElementById('password').value = '';
    }
});
