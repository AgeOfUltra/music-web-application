// Toast Notification System using Bootstrap Toast
function showToast(message, type = 'info') {
    console.log('showToast called:', message, type);
    const container = document.getElementById('toastContainer');
    if (!container) {
        console.error('Toast container not found!');
        return;
    }

    const icons = {
        success: '<i class="bi bi-check-circle-fill"></i>',
        error: '<i class="bi bi-x-circle-fill"></i>',
        warning: '<i class="bi bi-exclamation-triangle-fill"></i>',
        info: '<i class="bi bi-info-circle-fill"></i>'
    };

    const bgColors = {
        success: 'bg-success',
        error: 'bg-danger',
        warning: 'bg-warning',
        info: 'bg-info'
    };

    const toastEl = document.createElement('div');
    toastEl.className = 'toast align-items-center text-white border-0';
    toastEl.classList.add(bgColors[type]);
    toastEl.setAttribute('role', 'alert');
    toastEl.setAttribute('aria-live', 'assertive');
    toastEl.setAttribute('aria-atomic', 'true');

    toastEl.innerHTML = `
        <div class="d-flex">
            <div class="toast-body">
                ${icons[type]} ${message}
            </div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
        </div>
    `;

    container.appendChild(toastEl);
    const toast = new bootstrap.Toast(toastEl, { delay: 4000 });
    toast.show();

    toastEl.addEventListener('hidden.bs.toast', function () {
        toastEl.remove();
    });
}

// Check for server messages on page load
window.addEventListener('DOMContentLoaded', function() {
    console.log('DOM Loaded - Checking for messages...');
    console.log('Toast container exists:', !!document.getElementById('toastContainer'));

    const noDataElement = document.getElementById('noDataMessage');
    console.log('noDataMessage element:', noDataElement);
    console.log('noDataMessage content:', noDataElement ? noDataElement.textContent : 'N/A');
    if (noDataElement && noDataElement.textContent.trim() !== '') {
        console.log('Showing noData toast:', noDataElement.textContent);
        showToast(noDataElement.textContent, 'info');
    }

    const successElement = document.getElementById('successMessage');
    console.log('successMessage element:', successElement);
    console.log('successMessage content:', successElement ? successElement.textContent : 'N/A');
    if (successElement && successElement.textContent.trim() !== '') {
        console.log('Showing success toast:', successElement.textContent);
        showToast(successElement.textContent, 'success');
    }

    const errorElement = document.getElementById('errorMessage');
    console.log('errorMessage element:', errorElement);
    console.log('errorMessage content:', errorElement ? errorElement.textContent : 'N/A');
    if (errorElement && errorElement.textContent.trim() !== '') {
        console.log('Showing error toast:', errorElement.textContent);
        showToast(errorElement.textContent, 'error');
    }

    const noRequestDataElement = document.getElementById('noRequestDataMessage');
    console.log('noRequestDataMessage element:', noRequestDataElement);
    console.log('noRequestDataMessage content:', noRequestDataElement ? noRequestDataElement.textContent : 'N/A');
    if (noRequestDataElement && noRequestDataElement.textContent.trim() !== '') {
        console.log('Showing noRequestData toast:', noRequestDataElement.textContent);
        showToast(noRequestDataElement.textContent, 'info');
    }
});

// Message Modal Functions
function openMessageModal(button) {
    const message = button.getAttribute('data-message');
    document.getElementById('messageContent').innerHTML = message.replace(/\n/g, '<br>');
}

// Confess Update Modal Functions
function openConfessUpdateModal(button) {
    // Set all hidden fields
    document.getElementById('confessRoomHash').value = button.getAttribute('data-roomhash') || '';
    document.getElementById('confessInitiatedBy').value = button.getAttribute('data-initiatedby') || '';
    document.getElementById('confessSenderName').value = button.getAttribute('data-sendername') || '';
    document.getElementById('confessSenderEmail').value = button.getAttribute('data-senderemail') || '';
    document.getElementById('confessRoomName').value = button.getAttribute('data-roomname') || '';
    document.getElementById('confessReceiverAlias').value = button.getAttribute('data-receiveralias') || '';
    document.getElementById('confessType').value = button.getAttribute('data-confesstype') || '';
    document.getElementById('confessEmail').value = button.getAttribute('data-email') || '';
    document.getElementById('confessSongName').value = button.getAttribute('data-songname') || '';
    document.getElementById('confessSingerName').value = button.getAttribute('data-singername') || '';
    document.getElementById('confessMessage').value = button.getAttribute('data-message') || '';

    // Set display fields
    document.getElementById('displayInitiatedBy').textContent = button.getAttribute('data-initiatedby') || '';
    document.getElementById('displaySenderName').textContent = button.getAttribute('data-sendername') || '';
    document.getElementById('displaySenderEmail').textContent = button.getAttribute('data-senderemail') || '';
    document.getElementById('displayRoomName').textContent = button.getAttribute('data-roomname') || '';
    document.getElementById('displayReceiverAlias').textContent = button.getAttribute('data-receiveralias') || '';
    document.getElementById('displayConfessType').textContent = button.getAttribute('data-confesstype') || '';
    document.getElementById('displayEmail').textContent = button.getAttribute('data-email') || '';
    document.getElementById('displayConfessSongName').textContent = button.getAttribute('data-songname') || '';
    document.getElementById('displaySingerName').textContent = button.getAttribute('data-singername') || '';

    const status = button.getAttribute('data-status');
    const note = button.getAttribute('data-note');

    document.getElementById('confessStatus').value = '';
    document.getElementById('confessNote').value = note && note !== 'null' ? note : '';

    // Reset validation states
    document.getElementById('confessNote').classList.remove('is-invalid');
    document.getElementById('noteRequiredIndicator').style.display = 'none';
}

// Song Update Modal Functions
function openSongUpdateModal(button) {
    const requestor = button.getAttribute('data-requestor');
    const songName = button.getAttribute('data-songname');
    const movieName = button.getAttribute('data-moviename');
    const singerName = button.getAttribute('data-singername');
    const status = button.getAttribute('data-status');
    const note = button.getAttribute('data-note');

    // Set hidden fields
    document.getElementById('songRequestor').value = requestor;
    document.getElementById('songMovieName').value = movieName || '';
    document.getElementById('songSingerName').value = singerName;

    // Set display fields
    document.getElementById('displayRequestor').textContent = requestor;
    document.getElementById('songName').value = songName;
    document.getElementById('displayMovieName').textContent = movieName || 'N/A';
    document.getElementById('displaySongSingerName').textContent = singerName;
    document.getElementById('songStatus').value = status;
    document.getElementById('songNote').value = note && note !== 'null' ? note : '';
}

// Toggle note required based on status selection
function toggleNoteRequired(selectElement) {
    const noteField = document.getElementById('confessNote');
    const noteIndicator = document.getElementById('noteRequiredIndicator');

    if (selectElement.value === 'REJECTED') {
        noteField.setAttribute('required', 'required');
        noteIndicator.style.display = 'inline';
    } else {
        noteField.removeAttribute('required');
        noteIndicator.style.display = 'none';
        noteField.classList.remove('is-invalid');
    }
}

// Form validation for confess update
document.getElementById('confessUpdateForm').addEventListener('submit', function(e) {
    const status = document.getElementById('confessStatus').value;
    const note = document.getElementById('confessNote').value.trim();
    const noteField = document.getElementById('confessNote');

    if (status === 'REJECTED' && note === '') {
        e.preventDefault();
        noteField.classList.add('is-invalid');
        showToast('Please provide a reason when rejecting a request.', 'error');
        return false;
    }

    noteField.classList.remove('is-invalid');
    return true;
});

// Clean up modal content when closed
document.getElementById('messageModal').addEventListener('hidden.bs.modal', function () {
    document.getElementById('messageContent').innerHTML = '';
});

document.getElementById('confessUpdateModal').addEventListener('hidden.bs.modal', function () {
    document.getElementById('confessUpdateForm').reset();
    document.getElementById('displayInitiatedBy').textContent = '';
    document.getElementById('displaySenderName').textContent = '';
    document.getElementById('displaySenderEmail').textContent = '';
    document.getElementById('displayRoomName').textContent = '';
    document.getElementById('displayReceiverAlias').textContent = '';
    document.getElementById('displayConfessType').textContent = '';
    document.getElementById('displayEmail').textContent = '';
    document.getElementById('displayConfessSongName').textContent = '';
    document.getElementById('displaySingerName').textContent = '';
    document.getElementById('confessNote').classList.remove('is-invalid');
    document.getElementById('noteRequiredIndicator').style.display = 'none';
});

document.getElementById('songUpdateModal').addEventListener('hidden.bs.modal', function () {
    document.getElementById('songUpdateForm').reset();
    document.getElementById('displayRequestor').textContent = '';
    document.getElementById('displayMovieName').textContent = '';
    document.getElementById('displaySongSingerName').textContent = '';
});