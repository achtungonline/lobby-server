var Match = require("core/src/core/match.js");
var random = require("core/src/core/util/random.js");
var gameStateFunctions = require("core/src/core/game-state-functions.js");
import {wormColorIds, STEERING_RIGHT, STEERING_STRAIGHT, STEERING_LEFT} from "core/src/core/constants";

var ServerGame = require("./server-game.js");

var MAX_PLAYERS_PER_LOBBY = 5;
var TIME_BETWEEN_GAMES = 5000; // milliseconds
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

    function addPlayer(socketId, name) {
        if (playerMapping[socketId]) {
            throw Error("Adding existing player to room");
        }
        if (matchConfig.players.length >= MAX_PLAYERS_PER_LOBBY) {
            throw Error("Adding player to full room");
        }
        var player = {
            id: nextPlayerId + "",
            name,
            colorId: wormColorIds.find(id => matchConfig.players.every(p => p.colorId !== id)),
            ready: false
        };
        nextPlayerId++;
        matchConfig.players.forEach(p => p.ready = false);
        matchConfig.players.push(player);
        matchConfig.maxScore = 5*(matchConfig.players.length - 1);
        playerMapping[socketId] = player.id;
        sendMatchConfig();
        ioFunctions.lobbyEnter(socketId, { playerId: player.id, matchConfig });
        return player.id;
    }

    function sendMatchConfig() {
        ioFunctions.lobbyUpdate(lobbyId, matchConfig);
    }

    function playerLeave(socketId) {
        if (!hasMatchStarted()) {
            var index = matchConfig.players.findIndex(p => p.id === playerMapping[socketId]);
            matchConfig.players.splice(index, 1);
            matchConfig.maxScore = 5*(matchConfig.players.length - 1);
            matchConfig.players.forEach(p => p.ready = false);
            sendMatchConfig();
        }
        delete playerMapping[socketId];
    }

    function playerReady(socketId) {
        var player = matchConfig.players.find(p => p.id === playerMapping[socketId]);
        if (!player.ready) {
            player.ready = true;
            sendMatchConfig();
            if (matchConfig.players.length >= 2 && matchConfig.players.every(p => p.ready)) {
                startMatch();
            }
        }
    }

    function colorChange(socketId, newColorId) {
        if (wormColorIds.indexOf(newColorId) !== -1 && matchConfig.players.every(p => p.colorId !== newColorId)) {
            matchConfig.players.find(p => p.id === playerMapping[socketId]).colorId = newColorId;
            sendMatchConfig();
        }
    }

    function startMatch() {
        match = Match({ matchConfig });
        ioFunctions.matchStart(lobbyId);
        startGame();
    }

    function startGame() {
        var seed = random.generateSeed();
        game = match.prepareNextGame(seed);
        ioFunctions.gameStart(lobbyId, game.gameState);
        serverGame = ServerGame({
            game,
            onGameUpdate: function(update) {
                ioFunctions.gameUpdate(lobbyId, update);
            },
            onGameOver: gameOver
        });
        serverGame.start();
    }

    function gameOver() {
        match.addFinishedGameState(game.gameState);
        ioFunctions.gameOver(lobbyId);
        if (match.isMatchOver()) {
            ioFunctions.matchOver(lobbyId);
        } else {
            ioFunctions.gameCountdown(lobbyId, TIME_BETWEEN_GAMES);
            setTimeout(startGame, TIME_BETWEEN_GAMES);
        }
    }

    function setPlayerSteering(socketId, steering) {
        if (steering === STEERING_LEFT || steering === STEERING_STRAIGHT || steering === STEERING_RIGHT) {
            var playerId = playerMapping[socketId];
            if (serverGame) {
                serverGame.setPlayerSteering(playerId, steering);
            }
        }
    }

    function isFull() {
        return matchConfig.players.length >= MAX_PLAYERS_PER_LOBBY;
    }

    function hasMatchStarted() {
        return match !== null;
    }

    return {
        addPlayer,
        colorChange,
        hasMatchStarted,
        id: lobbyId,
        isFull,
        playerReady,
        playerLeave,
        setPlayerSteering
    }
};