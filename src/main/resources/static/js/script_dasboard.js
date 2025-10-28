function logout() {
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('username');
    localStorage.removeItem('currentRoom');
    document.cookie = 'jwtToken=; path=/; max-age=0; SameSite=Lax';
    window.location.href = '/app/music/public/login';
}

function openCreateModal() {
    document.getElementById('createModal').classList.add('active');
}

function closeCreateModal() {
    document.getElementById('createModal').classList.remove('active');
    document.getElementById('createRoomForm').reset();
    hideAlert('createAlert');
}

function openJoinModal() {
    document.getElementById('joinModal').classList.add('active');
}

function closeJoinModal() {
    document.getElementById('joinModal').classList.remove('active');
    document.getElementById('joinRoomForm').reset();
    hideAlert('joinAlert');
}

function hideAlert(elementId) {
    document.getElementById(elementId).style.display = 'none';
}

window.onclick = function(event) {
    const createModal = document.getElementById('createModal');
    const joinModal = document.getElementById('joinModal');

    if (event.target === createModal) {
        closeCreateModal();
    }
    if (event.target === joinModal) {
        closeJoinModal();
    }
}