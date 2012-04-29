function inArray(arr, element) {
	for ( var i = 0; i < arr.length; i++) {
		if (arr[i] === element)
			return true;
	}
	return false;
}

var ENGINEIO = "git://github.com/LearnBoost/engine.io.git";

try {
	run()
} catch (err) {
	var npm = require('npm');
	npm.load(function() {
		npm.install(ENGINEIO, function() {
			run();
		});

	});
}

function run() {
	var engine = require('engine.io'), port = parseInt(process.argv[2]), server = engine
			.listen(port), stdin = process.openStdin(), OPEN = "OPEN", CLOSE = "CLOSE", ERROR = "ERROR", OK = "OK";
	stdin.setEncoding('utf8');

	/*var protocols = process.argv[3].split(",");
	for ( var k in engine.transport) {
		if (inArray(protocols, k) === false) {
			console.log(k);
			delete engine.transport[k];
		}
	}*/

	server.on('connection', function(socket) {
		process.stderr.write("New Socket\n");
		process.stdout.write(OPEN+"\n");
		stdin.on('data', function(chunk) {
			switch (chuck) {
			case CLOSE:
				socket.close();
			default:
				socket.send(chuck);
			}
		});

		socket.on('message', function(data) {
			process.stderr.write("Message received\n");
			process.stdout.write(data + "\n");
		});

		socket.on('close', function() {
			process.stderr.write("Socket closed\n");
			process.stdout.write(CLOSE + "\n");
		});
	});
	process.stdout.write(OK+"\n");

}