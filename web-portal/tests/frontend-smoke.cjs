// Zero-dependency frontend smoke checks for the static dashboard.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const { execFileSync } = require("node:child_process");
const os = require("node:os");
const path = require("node:path");

const html = fs.readFileSync(path.join(__dirname, "..", "index.html"), "utf8");
const scripts = [...html.matchAll(/<script(?:\\s[^>]*)?>([\\s\\S]*?)<\\/script>/gi)];
assert.equal(scripts.length, 1, "Expected one inline dashboard script");
const source = scripts[0][1];
const scratch = fs.mkdtempSync(path.join(os.tmpdir(), "expert-portal-js-"));
try {
  const jsFile = path.join(scratch, "dashboard.js");
  fs.writeFileSync(jsFile, source);
  execFileSync(process.execPath, ["--check", jsFile], { stdio: "pipe" });
} finally {
  fs.rmSync(scratch, { recursive: true, force: true });
}

const match = source.match(/function validateApiBase\\(base\\)\\{[^\\n]+\\}/);
assert.ok(match, "API URL validator should exist");
const validateApiBase = vm.runInNewContext("(" + match[0] + ")", { URL, Error });
assert.equal(validateApiBase("http://127.0.0.1:8000"), "http://127.0.0.1:8000");
assert.equal(validateApiBase("http://localhost:8000"), "http://localhost:8000");
assert.equal(validateApiBase("https://api.example.com/"), "https://api.example.com");
for (const unsafe of [
  "http://api.example.com",
  "http://192.168.1.1:8000",
  "ftp://api.example.com",
  "https://user:pass@api.example.com",
  "https://api.example.com/private",
  "https://api.example.com?token=secret",
  "javascript:alert(1)",
]) {
  assert.throws(() => validateApiBase(unsafe), undefined, unsafe);
}
console.log("Frontend syntax and HTTPS/localhost API origin validation: PASS");
