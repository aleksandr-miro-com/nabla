import WebSocket from 'ws';
import readline from 'readline';

const ws = new WebSocket('ws:localhost:5060/ws/foobar', {});
ws.binaryType = 'arraybuffer';

ws.on('open', () => {
  console.log('connected');
  ws.send('{"type": "ping"}');
});

ws.on('message', (msg) => {
  console.log('received:', msg);
  if (msg.constructor !== ArrayBuffer) {
   return;
  }
  const decoder = new TextDecoder("utf-8");
  const text = decoder.decode(msg);
  console.log('decoded:', text);
  console.log();
});

ws.on('close', (code, reason) => {
  console.log('closed:', code, reason.toString());
  process.exit();
});

ws.on('error', (err) => {
  console.error('error:', err);
});

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

rl.on('line', (line) => {
  ws.send(line);
  console.log();

  if (line === 'quit') {
    rl.close();
  }
});