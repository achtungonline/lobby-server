var io = require('socket.io')({ serveClient: false });

var Match = require("core/src/core/match.js");
var random = require("core/src/core/util/random.js");
var gameStateFunctions = require("core/src/core/game-state-functions.js");

var ServerGame = require("./server-game.js");

var PLAYERS_PER_ROOM = 2;
var nextRoomId = 0;
var rooms = [];

function Room() {
    var roomId = nextRoomId + "";
    nextRoomId++;
    var nextPlayerId = 0;
    var players = [];
    var match = null;
    var game = null;
    var serverGame = null;

    function addPlayer(socket) {
        if (players.length >= PLAYERS_PER_ROOM) {
            throw Error("Adding player to full room");
        }
        var player = {
            id: nextPlayerId + "",
            socket: socket,
            ready: false
        };
        nextPlayerId++;
        players.push(player);
        return player.id;
    }

    function removePlayer(playerId) {
        var index = players.findIndex(p => p.id === playerId);
        players.splice(index, 1);
    }

    function playerReady(playerId) {
        console.log(playerId + " is ready");
        var player = players.find(p => p.id === playerId);
        player.ready = true;
        if (players.length === PLAYERS_PER_ROOM && players.every(p => p.ready)) {
            startGame();
        }
    }

    function startGame() {
        var matchConfig = {
            players: players.map(p => ({id: p.id})),
            map: gameStateFunctions.createMapSquare("Square 500", 500),
            maxScore: 5*(players.length - 1)
        };
        match = Match({ matchConfig });
        var seed = random.generateSeed();
        game = match.prepareNextGame(seed);
        players.forEach(player => {
            player.socket.emit("start_game", game.gameState);
        });
        serverGame = ServerGame({
            game,
            onGameUpdate: function(update) {
                players.forEach(player => {
                    player.socket.emit("game_updated", update);
                });
            },
            onGameOver: function() {
                players.forEach(player => {
                    player.socket.emit("game_over");
                });
                console.log("Game " + roomId + " over");
                removeRoom(roomId);
            }
        });
        serverGame.start();
    }

    function setPlayerSteering(playerId, steering) {
        if (serverGame) {
            serverGame.setPlayerSteering(playerId, steering);
        }
    }

    function isFull() {
        return players.length === PLAYERS_PER_ROOM;
    }

    return {
        addPlayer,
        id: roomId,
        isFull,
        playerReady,
        removePlayer,
        setPlayerSteering
    }
}

function addRoom() {
    var room = Room();
    rooms.push(room);
    console.log("Created room " + room.id);
    return room;
}

function getRoom(roomId) {
    return rooms.find(r => r.id === roomId);
}

function getVacantRoomId() {
    if (rooms.length === 0 || rooms[rooms.length - 1].isFull()) {
        return addRoom().id;
    } else {
        return rooms[rooms.length - 1].id;
    }
}

function removeRoom(roomId) {
    var index = rooms.findIndex(r => r.id === roomId);
    rooms.splice(index, 1);
    console.log("Room " + roomId + " removed");
}

io.on('connection', function(socket){
    console.log("Client connected: " + socket.id);
    var roomId = getVacantRoomId();
    var playerId = getRoom(roomId).addPlayer(socket);
    socket.emit("player_id", playerId);
    console.log("Added player " + playerId + " to room " + roomId);

    socket.on("ready", () => {
        var room = getRoom(roomId);
        if (room) {
            room.playerReady(playerId);
        }
    });

    socket.on("player_steering", steering => {
        var room = getRoom(roomId);
        if (room) {
            room.setPlayerSteering(playerId, steering[playerId]);
        }
    });

    socket.on("disconnect", function() {
        var room = getRoom(roomId);
        if (room) {
            room.removePlayer(playerId);
        }
        console.log("Client disconnected: " + socket.id);
    });
});
io.listen(3000);
console.log("Server started");