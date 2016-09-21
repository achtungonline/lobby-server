var Match = require("core/src/core/match.js");
var random = require("core/src/core/util/random.js");
var gameStateFunctions = require("core/src/core/game-state-functions.js");
var wormColorIds = require("core/src/core/constants").wormColorIds;

var ServerGame = require("./server-game.js");

var MAX_PLAYERS_PER_LOBBY = 5;
var nextLobbyId = 0;

module.exports = function Lobby(ioFunctions) {
    var lobbyId = nextLobbyId + "";
    nextLobbyId++;
    var nextPlayerId = 0;
    var matchConfig = {
        players: [],
        map: gameStateFunctions.createMapSquare("Square 500", 500),
        maxScore: 0
    };
    var playerMapping = {};
    var match = null;
    var game = null;
    var serverGame = null;

    function addPlayer(socketId, playerData) {
        if (playerMapping[socketId]) {
            throw Error("Adding existing player to room");
        }
        if (matchConfig.players.length >= MAX_PLAYERS_PER_LOBBY) {
            throw Error("Adding player to full room");
        }
        var player = {
            id: nextPlayerId + "",
            name: playerData.name,
            colorId: wormColorIds.find(id => matchConfig.players.every(p => p.colorId !== id)),
            ready: false
        };
        nextPlayerId++;
        matchConfig.players.push(player);
        matchConfig.maxScore = 5*(matchConfig.players.length - 1);
        playerMapping[socketId] = player.id;
        emitMatchConfig();
        ioFunctions.lobbyEnter(socketId, { playerId: player.id, matchConfig });
        return player.id;
    }

    function emitMatchConfig() {
        ioFunctions.lobbyUpdate(lobbyId, matchConfig);
    }

    function removePlayer(socketId) {
        var index = matchConfig.players.findIndex(p => p.id === playerMapping[socketId]);
        matchConfig.players.splice(index, 1);
        matchConfig.maxScore = 5*(matchConfig.players.length - 1);
        emitMatchConfig();
    }

    function playerReady(socketId) {
        console.log(socketId + " is ready");
        var player = matchConfig.players.find(p => p.id === playerMapping[socketId]);
        player.ready = true;
        emitMatchConfig();
        if (matchConfig.players.length >= 2&& matchConfig.players.every(p => p.ready)) {
            startGame();
        }
    }

    function colorChange(socketId, newColorId) {
        // TODO Check that color isn't used
        matchConfig.players.find(p => p.id === playerMapping[socketId]).colorId = newColorId;
        emitMatchConfig();
    }

    function startGame() {
        match = Match({ matchConfig });
        var seed = random.generateSeed();
        game = match.prepareNextGame(seed);
        ioFunctions.gameStart(lobbyId, game.gameState);
        serverGame = ServerGame({
            game,
            onGameUpdate: function(update) {
                ioFunctions.gameUpdate(lobbyId, update);
            },
            onGameOver: function() {
                ioFunctions.gameOver(lobbyId);
                console.log("Game " + lobbyId + " over");
            }
        });
        serverGame.start();
    }

    function setPlayerSteering(socketId, steering) {
        var playerId = playerMapping[socketId];
        if (serverGame) {
            serverGame.setPlayerSteering(playerId, steering[playerId]);
        }
    }

    function isFull() {
        return matchConfig.players.length === MAX_PLAYERS_PER_LOBBY;
    }

    return {
        addPlayer,
        colorChange,
        id: lobbyId,
        isFull,
        playerReady,
        removePlayer,
        setPlayerSteering
    }
}