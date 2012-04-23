var engine = require('engine.io')
  , port = parseInt(process.argv[2])
  , server = engine.listen(port)
  , stdin = process.openStdin()
  , OPEN = "OPEN"
  , CLOSE = "CLOSE"
  , ERROR = "ERROR"

stdin.setEncoding('utf8');

linebuffer = ""

server.on('connection', function(socket) {
	process.stdout.write(OPEN);
	stdin.on('data', function(chunk) {
		switch(chuck) {
		case CLOSE:
			socket.close();
		default:
			socket.send(chuck);
		}
	});
	
	socket.on('message', function(data) {
		process.stdout.write(data+"\n");
	});

	socket.on('close', function() {
		process.stdout.write(CLOSE+"\n");
	});
});
process.stdout.write("OK");
