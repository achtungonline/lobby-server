var gameStateFunctions = require("core/src/core/game-state-functions.js");
var forEach = require("core/src/core/util/for-each.js");
var compression = require("core/src/core/util/compression.js");

var UPDATE_TICK = 15;
var CLIENT_UPDATE_TICK = 45;

function DeltaUpdateHandler(gameState) {
    var wormPathSegmentCounts = {};
    var gameEventCount = 0;
    var powerUpEventCount = 0;
    var effectEventCount = 0;

    function extractGameStateChanges() {
        var wormPathSegments = {};
        forEach(gameState.wormPathSegments, function(segments, id) {
            if (wormPathSegmentCounts[id] === undefined) {
                wormPathSegmentCounts[id] = 0;
            }
            if (segments.length > 0) {
                if (wormPathSegmentCounts[id] < segments.length) {
                    wormPathSegments[id] = [];
                    while (wormPathSegmentCounts[id] < segments.length) {
                        wormPathSegments[id].push(compression.compressWormSegment(segments[wormPathSegmentCounts[id]]));
                        wormPathSegmentCounts[id]++;
                    }
                } else {
                    wormPathSegments[id] = [compression.compressWormSegment(segments[segments.length - 1])];
                }
            }
        });
        var gameEvents = [];
        while (gameEventCount < gameState.gameEvents.length) {
            gameEvents.push(gameState.gameEvents[gameEventCount]);
            gameEventCount++;
        }
        var powerUpEvents = [];
        while (powerUpEventCount < gameState.powerUpEvents.length) {
            powerUpEvents.push(gameState.powerUpEvents[powerUpEventCount]);
            powerUpEventCount++;
        }
        var effectEvents = [];
        while (effectEventCount < gameState.effectEvents.length) {
            effectEvents.push(gameState.effectEvents[effectEventCount]);
            effectEventCount++;
        }
        return {
            gameTime: gameState.gameTime,
            wormPathSegments,
            gameEvents,
            powerUpEvents,
            effectEvents
        }
    }

    return {
        extractGameStateChanges
    };
}

module.exports = function ServerGame({ game, onGameUpdate, onGameOver }) {

    var deltaUpdateHandler = DeltaUpdateHandler(game.gameState);

    var localGameState = {
        previousUpdateTime: 0,
        previousClientUpdateTime: 0,
        nextUpdateTime: 0
    };

    function setPlayerSteering(playerId, steering) {
        gameStateFunctions.setPlayerSteering(game.gameState, playerId, steering);
    }

    function start() {
        localGameState.previousUpdateTime = Date.now();
        localGameState.previousClientUpdateTime = localGameState.previousUpdateTime - 2*CLIENT_UPDATE_TICK;
        localGameState.nextUpdateTime = localGameState.previousUpdateTime + UPDATE_TICK;
        game.start();
        setTimeout(update, UPDATE_TICK);
    }

    function update() {
        var updateStartTime = Date.now();
        if (game.isActive()) {
            var deltaTime = (updateStartTime - localGameState.previousUpdateTime) / 1000;
            game.update(deltaTime);
            if (updateStartTime - localGameState.previousClientUpdateTime >= CLIENT_UPDATE_TICK || !game.isActive()) {
                if (onGameUpdate) {
                    onGameUpdate(deltaUpdateHandler.extractGameStateChanges());
                }
                localGameState.previousClientUpdateTime = updateStartTime
            }
        }
        localGameState.previousUpdateTime = updateStartTime;

        if (game.isActive()) {
            localGameState.nextUpdateTime += UPDATE_TICK;
            var currentTime = Date.now();
            while (currentTime >= localGameState.nextUpdateTime) {
                localGameState.nextUpdateTime += UPDATE_TICK;
            }
            var sleepTime = localGameState.nextUpdateTime - currentTime;
            setTimeout(update, sleepTime);
        } else {
            if (onGameOver) {
                onGameOver();
            }
        }
    }

    function stop() {
        game.stop();
    }

    return {
        start,
        stop,
        setPlayerSteering,
        isActive: game.isActive,
        gameState: game.gameState
    };
};
