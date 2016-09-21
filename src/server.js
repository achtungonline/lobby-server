var io = require('socket.io')({ serveClient: false });

var forEach = require("core/src/core/util/for-each");

var Lobby = require("./lobby.js");

var lobbies = [];
var socketData = {};

var sendFunctions = {
    lobbyEnter: (socketId,data) => {
        io.to(socketId).emit("lobby_enter", data);
    },
    lobbyUpdate: (lobbyId,data) => {
        io.to(lobbyId).emit("lobby_update", data)
    },
    gameStart: (lobbyId,data) => {
        io.to(lobbyId).emit("game_start", data);
    },
    gameUpdate: (lobbyId,data) => {
        io.to(lobbyId).emit("game_update", data);
    },
    gameOver: (lobbyId) => {
        io.to(lobbyId).emit("game_over");
    }
};

function addLobby() {
    var lobby = Lobby(sendFunctions);
    lobbies.push(lobby);
    console.log("Created lobby " + lobby.id);
    return lobby;
}

function assignPlayerToLobby(socketId) {
    var lobbyId = getVacantLobbyId();
    var playerId = getLobby(lobbyId).addPlayer(socketId, socketData[socketId].playerData);
    socketData[socketId].lobbyId = lobbyId;
    io.sockets.connected[socketId].join(lobbyId);
    console.log("Added player " + socketId + " to lobby " + lobbyId + " as player " + playerId);
}

function getLobby(lobbyId) {
    return lobbies.find(lobby => lobby.id === lobbyId);
}

function getVacantLobbyId() {
    var lobby = lobbies.find(r => !r.isFull());
    if (lobby) {
        return lobby.id;
    } else {
        return addLobby().id;
    }
}

function playerReady(socketId) {
    var lobbyId = socketData[socketId].lobbyId;
    var lobby = getLobby(lobbyId);
    if (lobby) {
        lobby.playerReady(socketId);
    }
}

function playerSteering(socketId, steering) {
    var lobbyId = socketData[socketId].lobbyId;
    var lobby = getLobby(lobbyId);
    if (lobby) {
        lobby.setPlayerSteering(socketId, steering);
    }
}

function playerDisconnect(socketId) {
    var lobbyId = socketData[socketId].lobbyId;
    var lobby = getLobby(lobbyId);
    if (lobby) {
        lobby.removePlayer(socketId);
    }
    console.log("Client disconnected: " + socketId);
}

function playerEnter(socketId, playerData) {
    socketData[socketId].playerData = playerData;
    assignPlayerToLobby(socketId);
}

function playerColorChange(socketId, newColorId) {
    var lobbyId = socketData[socketId].lobbyId;
    var lobby = getLobby(lobbyId);
    lobby.colorChange(socketId, newColorId);
}

var protocol = {
    "ready": playerReady,
    "player_steering": playerSteering,
    "disconnect": playerDisconnect,
    "enter": playerEnter,
    "color_change": playerColorChange
};

io.on('connection', function(socket){
    console.log("Client connected: " + socket.id);
    socketData[socket.id] = {
        lobbyId: undefined
    };
    forEach(protocol, (f,name) => socket.on(name, f.bind(this, socket.id)));
});
io.listen(3000);
console.log("Server started");