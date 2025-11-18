// Toast notification system
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
            <button type="button" class="toast-close" aria-label="Close">×</button>
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

// Initialize notifier
const notifier = new ToastNotifier();

// Modal functions for Create Room
function openCreateModal() {
    document.getElementById('createModal').classList.add('active');
}

function closeCreateModal() {
    document.getElementById('createModal').classList.remove('active');
    document.getElementById('createRoomForm').reset();
}

// Modal functions for Join Room
function openJoinModal() {
    document.getElementById('joinModal').classList.add('active');
}

function closeJoinModal() {
    document.getElementById('joinModal').classList.remove('active');
    document.getElementById('joinRoomForm').reset();
}

// Modal Functions for Send Confess
function openSendConfessModal() {
    document.getElementById('sendConfess').classList.add('active');
}

function closeSendConfessModal() {
    document.getElementById('sendConfess').classList.remove('active');
    document.getElementById('sendConfessForm').reset();
    // Reset the "Other" input field visibility
    document.getElementById('otherConfessTypeDiv').style.display = 'none';
    document.getElementById('otherConfessType').required = false;
}

// Toggle "Other" input field for confess type
function toggleOtherInput() {
    const confessType = document.getElementById('confessType').value;
    const otherDiv = document.getElementById('otherConfessTypeDiv');
    const otherInput = document.getElementById('otherConfessType');

    if (confessType === 'other') {
        otherDiv.style.display = 'block';
        otherInput.required = true;
    } else {
        otherDiv.style.display = 'none';
        otherInput.required = false;
        otherInput.value = '';
    }
}

// Logout function
function logout() {
    window.location.href = '/app/music/public/logout';
}

// Close modal when clicking outside
window.onclick = function(event) {
    const createModal = document.getElementById('createModal');
    const joinModal = document.getElementById('joinModal');
    const sendConfessModal = document.getElementById('sendConfess');

    if (event.target === createModal) {
        closeCreateModal();
    }
    if (event.target === joinModal) {
        closeJoinModal();
    }
    if (event.target === sendConfessModal) {
        closeSendConfessModal();
    }
}

// Handle error and success messages on page load
window.addEventListener('DOMContentLoaded', function() {
    let hasErrors = false;
    let hasConfessErrors = false;

    // Handle field validation errors for Create Room
    const fieldErrorsContainer = document.getElementById('fieldErrors');
    if (fieldErrorsContainer) {
        const errorItems = fieldErrorsContainer.querySelectorAll('.error-item');
        errorItems.forEach(item => {
            const errorMessage = item.textContent.trim();
            if (errorMessage) {
                notifier.error(errorMessage);
                hasErrors = true;
            }
        });
    }

    // Handle field validation errors for Confess Form
    const confessFieldErrorsContainer = document.getElementById('confessFieldErrors');
    if (confessFieldErrorsContainer) {
        const errorItems = confessFieldErrorsContainer.querySelectorAll('.error-item');
        errorItems.forEach(item => {
            const errorMessage = item.textContent.trim();
            if (errorMessage) {
                notifier.error(errorMessage);
                hasConfessErrors = true;
            }
        });
    }

    // Handle creation error
    const creationErrorContainer = document.getElementById('creationError');
    if (creationErrorContainer) {
        const creationError = creationErrorContainer.textContent.trim();
        if (creationError) {
            notifier.error(creationError);
            openCreateModal();
            hasErrors = true;
        }
    }

    // Handle join error
    const joinErrorContainer = document.getElementById('joinError');
    if (joinErrorContainer) {
        const joinError = joinErrorContainer.textContent.trim();
        if (joinError) {
            notifier.error(joinError);
            openJoinModal();
            hasErrors = true;
        }
    }

    // Handle email status from Send Confess form
    const urlParams = new URLSearchParams(window.location.search);
    const status = urlParams.get('status');

    // Check if there's an emailStatus attribute from Thymeleaf
    const emailStatusElement = document.getElementById('emailStatus');
    if (emailStatusElement) {
        const emailStatus = emailStatusElement.textContent.trim();
        if (emailStatus) {
            if (emailStatus.toLowerCase().includes('success')) {
                notifier.success(emailStatus);
            } else {
                notifier.error(emailStatus);
                hasConfessErrors = true;
            }
        }
    }

    // Also check URL parameter for status
    if (status === 'sentSuccess') {
        notifier.success('Successfully sent your confession!');
    } else if (status === 'failed') {
        notifier.error('Failed to send confession. Please try again.');
        hasConfessErrors = true;
    }

    // Open the appropriate modal if there are errors
    if (hasConfessErrors) {
        openSendConfessModal();
    }

    // Clear fields ONLY if there are actual errors
    if (hasErrors) {
        const roomNameField = document.getElementById('roomName');
        const maxCountField = document.getElementById('maxCount');
        const joinRoomNameField = document.getElementById('joinRoomName');

        if (roomNameField) roomNameField.value = '';
        if (maxCountField) maxCountField.value = '';
        if (joinRoomNameField) joinRoomNameField.value = '';
    }
});