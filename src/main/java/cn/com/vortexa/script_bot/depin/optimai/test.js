const readline = require('readline');

function Ts(e) {
    let t = 0, i = 1;
    for (let s = 0; s < e; s++) [t, i] = [i, t + i];
    return t % 20;
}
function Bs(e) {
    return [...e].map((t, i) => String.fromCharCode(t.charCodeAt(0) + Ts(i))).join("");
}
function Rs(e) {
    return [...e].map((t, i) => String.fromCharCode((t.charCodeAt(0) ^ i % 256) & 255)).join("");
}
function Ss(e) {
    const t = [...e];
    for (let i = 0; i < t.length - 1; i += 2) [t[i], t[i + 1]] = [t[i + 1], t[i]];
    return t.join("");
}
function Ur(e) {
    return btoa(Ss(Rs(Bs(JSON.stringify(e)))));
}
function register(userId, timestamp) {
    return {
        user_id: userId,
        device_info: {
            "cpu_cores": 1,
            "memory_gb": 0,
            "screen_width_px": 375,
            "screen_height_px": 600,
            "color_depth": 30,
            "scale_factor": 1,
            "browser_name": "chrome",
            "device_type": "extension",
            "language": "id-ID",
            "timezone": "Asia/Jakarta"
        },
        browser_name: "chrome",
        device_type: "extension",
        timestamp: timestamp
    }
}
function uptime(userId, deviceId, timestamp) {
    return {
        duration: 600000,
        user_id: userId,
        device_id: deviceId,
        device_type: "telegram",
        timestamp: timestamp
    }
}
function generatePayload(payload) {
    return Ur(payload)
}
function main() {
   let s = generatePayload(uptime("c17c9b6a-c261-4870-aac6-7a580e330d58", "0195c17f-661d-70eb-8726-5c56303832b5", 1744264244445))

    console.log(s)
}

main();
