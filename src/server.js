var io = require('socket.io')({ serveClient: false });

var Match = require("core/src/core/match.js");
var random = require("core/src/core/util/random.js");
var forEach = require("core/src/core/util/for-each.js");

var ServerGame = require("./server-game.js");

io.on('connection', function(socket){
    var match = null;
    var serverGame = null;

    socket.on("match_config", function(matchConfig) {
        match = Match({ matchConfig });
    });
    socket.on("start_game", function() {
        var seed = random.generateSeed();
        var game = match.prepareNextGame(seed);
        socket.emit("start_game", game.gameState);
        serverGame = ServerGame({
            game,
            onGameUpdate: function(update) {
                socket.emit("game_updated", update);
            },
            onGameOver: function() {
                socket.emit("game_over");
                console.log("Game over");
            }
        });
        serverGame.start();
    });
    socket.on("player_steering", function(data) {
        forEach(data, function(steering, playerId) {
            serverGame.setPlayerSteering(playerId, steering);
        })
    });
    socket.on("disconnect", function() {
        console.log("Client disconnected");
    });

    console.log("Client connected");
});
io.listen(3000);
console.log("Server started");