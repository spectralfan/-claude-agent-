// ============================================================
// 跳跳乐 (Jump Jump) — Canvas 实现
// 玩法：角色自动前进，点击/空格蓄力跳跃，平台得分，掉落结束
// ============================================================

const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');

// ---------- 尺寸 ----------
const W = 480, H = 720;
canvas.width = W;
canvas.height = H;

// ---------- DOM 引用 ----------
const scoreEl = document.getElementById('score');
const gameOverEl = document.getElementById('gameOver');
const finalScoreEl = document.getElementById('finalScore');
const restartBtn = document.getElementById('restartBtn');

// ---------- 游戏状态 ----------
const STATE = { PLAYING: 0, CHARGING: 1, JUMPING: 2, GAMEOVER: 3 };
let state = STATE.PLAYING;

// ---------- 玩家 ----------
const player = {
    x: 120,
    y: 0,
    width: 30,
    height: 40,
    vy: 0,
    vx: 0,
    grounded: false,
    currentPlatform: null,
    charging: false,
    chargePower: 0,
    maxCharge: 15,      // 最大蓄力跳跃力度
    chargeSpeed: 0.25,  // 蓄力增长速度
};

// ---------- 平台 ----------
let platforms = [];
const PLATFORM_WIDTH = 80;
const PLATFORM_HEIGHT = 16;
const PLATFORM_GAP_X = [50, 150];   // 水平间距范围
const PLATFORM_GAP_Y = [80, 140];   // 垂直间距范围
const HORIZONTAL_SPEED = 1.2;       // 自动前进速度

// ---------- 相机 ----------
let cameraY = 0;
let score = 0;
let bestScore = parseInt(localStorage.getItem('jumpBest')) || 0;

// ---------- 粒子效果 ----------
let particles = [];

// ---------- 初始化 ----------
function init() {
    platforms = [];
    particles = [];
    state = STATE.PLAYING;
    score = 0;
    cameraY = 0;

    // 初始平台（玩家脚下）
    platforms.push({
        x: 60,
        y: H - 80,
        w: PLATFORM_WIDTH,
        h: PLATFORM_HEIGHT,
        color: '#e94560',
        scored: false
    });

    // 生成后续平台
    player.x = 60 + PLATFORM_WIDTH / 2 - player.width / 2;
    player.y = H - 80 - player.height;
    player.vy = 0;
    player.vx = 0;
    player.grounded = true;
    player.currentPlatform = platforms[0];
    player.chargePower = 0;

    // 预生成一些平台
    let lastPlat = platforms[0];
    for (let i = 0; i < 12; i++) {
        lastPlat = generateNextPlatform(lastPlat);
    }

    updateScore();
    gameOverEl.style.display = 'none';
}

// ---------- 生成下一个平台 ----------
function generateNextPlatform(prev) {
    const gapX = rand(PLATFORM_GAP_X[0], PLATFORM_GAP_X[1]);
    const gapY = rand(PLATFORM_GAP_Y[0], PLATFORM_GAP_Y[1]);

    let newX = prev.x + gapX;
    // 如果太靠右就折返
    if (newX + PLATFORM_WIDTH > W - 20) {
        newX = prev.x - gapX;
    }
    // 如果太靠左也折返
    if (newX < 20) {
        newX = prev.x + gapX;
    }

    const newY = prev.y - gapY;
    const hues = [340, 200, 120, 40, 280];
    const hue = hues[Math.floor(Math.random() * hues.length)];

    const plat = {
        x: newX,
        y: newY,
        w: PLATFORM_WIDTH,
        h: PLATFORM_HEIGHT,
        color: `hsl(${hue}, 80%, 60%)`,
        scored: false
    };
    platforms.push(plat);
    return plat;
}

// ---------- 工具函数 ----------
function rand(min, max) {
    return Math.random() * (max - min) + min;
}

// ---------- 更新分数显示 ----------
function updateScore() {
    scoreEl.textContent = `分数: ${score}`;
}

// ---------- 输入处理 ----------
canvas.addEventListener('mousedown', (e) => {
    if (state === STATE.GAMEOVER) return;
    if (state === STATE.PLAYING && player.grounded) {
        state = STATE.CHARGING;
        player.charging = true;
        player.chargePower = 0;
    }
});

canvas.addEventListener('mouseup', (e) => {
    if (state === STATE.CHARGING) {
        jump();
    }
});

// 触摸事件
canvas.addEventListener('touchstart', (e) => {
    e.preventDefault();
    if (state === STATE.GAMEOVER) return;
    if (state === STATE.PLAYING && player.grounded) {
        state = STATE.CHARGING;
        player.charging = true;
        player.chargePower = 0;
    }
});

canvas.addEventListener('touchend', (e) => {
    e.preventDefault();
    if (state === STATE.CHARGING) {
        jump();
    }
});

// 键盘事件
document.addEventListener('keydown', (e) => {
    if (e.key === ' ' || e.key === 'Space') {
        e.preventDefault();
        if (state === STATE.GAMEOVER) return;
        if (state === STATE.PLAYING && player.grounded) {
            state = STATE.CHARGING;
            player.charging = true;
            player.chargePower = 0;
        }
    }
});

document.addEventListener('keyup', (e) => {
    if (e.key === ' ' || e.key === 'Space') {
        e.preventDefault();
        if (state === STATE.CHARGING) {
            jump();
        }
    }
});

// ---------- 跳跃 ----------
function jump() {
    state = STATE.JUMPING;
    player.charging = false;
    player.grounded = false;

    // 力度映射到跳跃速度和水平速度
    const power = player.chargePower / player.maxCharge;
    const jumpVY = -(8 + power * 10);      // 垂直初速度
    const jumpVX = (1 + power * 2);         // 水平初速度

    player.vy = jumpVY;
    // 自动前进方向：根据平台大致方向
    if (player.currentPlatform) {
        const next = findNextPlatform(player.currentPlatform);
        if (next) {
            player.vx = next.x > player.currentPlatform.x ? jumpVX : -jumpVX * 0.8;
        } else {
            player.vx = jumpVX;
        }
    } else {
        player.vx = jumpVX;
    }

    player.currentPlatform = null;
}

// ---------- 找下一个平台 ----------
function findNextPlatform(current) {
    let best = null;
    let bestDist = Infinity;
    for (const p of platforms) {
        const dy = current.y - p.y;
        if (dy > 10 && dy < 200) {
            const dist = Math.abs(p.x - current.x);
            if (dist < bestDist && dy > 0) {
                bestDist = dist;
                best = p;
            }
        }
    }
    return best;
}

// ---------- 生成着陆粒子 ----------
function spawnLandParticles(x, y) {
    for (let i = 0; i < 12; i++) {
        particles.push({
            x: x + rand(-10, 10),
            y: y,
            vx: rand(-3, 3),
            vy: rand(-5, -1),
            life: 1,
            decay: 0.02 + rand(0, 0.03),
            size: rand(2, 5),
            color: `hsl(${rand(30, 60)}, 100%, ${rand(50, 80)}%)`
        });
    }
}

// ---------- 更新 ----------
function update() {
    if (state === STATE.GAMEOVER) return;

    // ---- 蓄力 ----
    if (state === STATE.CHARGING) {
        player.chargePower = Math.min(player.chargePower + player.chargeSpeed, player.maxCharge);
    }

    // ---- 跳跃物理 ----
    if (state === STATE.JUMPING || !player.grounded) {
        player.vy += 0.5;   // 重力
        player.x += player.vx;
        player.y += player.vy;

        // 水平边界
        if (player.x < 0) player.x = 0;
        if (player.x + player.width > W) player.x = W - player.width;

        // 检测着陆
        player.grounded = false;
        for (const plat of platforms) {
            if (rectCollide(player, plat)) {
                // 只有当玩家下落时才着陆
                if (player.vy > 0) {
                    player.y = plat.y - player.height;
                    player.vy = 0;
                    player.vx = HORIZONTAL_SPEED;  // 恢复自动前进
                    player.grounded = true;
                    player.currentPlatform = plat;

                    // 计分（每个平台只计一次）
                    if (!plat.scored && plat.y < platforms[0].y) {
                        plat.scored = true;
                        score++;
                        updateScore();
                        spawnLandParticles(player.x + player.width / 2, player.y + player.height);
                    }

                    state = STATE.PLAYING;
                    break;
                }
            }
        }

        // 自动前进（地面时）
    } else if (state === STATE.PLAYING && player.grounded) {
        player.x += HORIZONTAL_SPEED;
        // 如果角色超出平台边缘，下落
        if (player.currentPlatform) {
            const plat = player.currentPlatform;
            if (player.x + player.width < plat.x || player.x > plat.x + plat.w) {
                player.grounded = false;
                player.vy = 1;
                player.vx = HORIZONTAL_SPEED * 0.5;
            }
        }
    }

    // ---- 掉落检测 ----
    if (player.y > H + 100) {
        gameOver();
        return;
    }

    // ---- 相机跟随 ----
    const targetCamY = player.y - H * 0.4;
    cameraY += (targetCamY - cameraY) * 0.08;

    // ---- 生成新平台（保持视野内充足） ----
    const lowestVisibleY = cameraY + H + 100;
    const highestVisibleY = cameraY - 100;
    let lastPlat = platforms[platforms.length - 1];
    while (lastPlat.y > highestVisibleY) {
        lastPlat = generateNextPlatform(lastPlat);
    }
    // 移除视野外太远的平台（保留之前的用于计分）
    platforms = platforms.filter(p => p.y > cameraY - 300);

    // ---- 更新粒子 ----
    for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i];
        p.x += p.vx;
        p.y += p.vy;
        p.vy += 0.1;
        p.life -= p.decay;
        if (p.life <= 0) {
            particles.splice(i, 1);
        }
    }
}

// ---------- 碰撞检测 ----------
function rectCollide(player, plat) {
    return player.x < plat.x + plat.w &&
           player.x + player.width > plat.x &&
           player.y < plat.y + plat.h &&
           player.y + player.height > plat.y;
}

// ---------- 游戏结束 ----------
function gameOver() {
    state = STATE.GAMEOVER;
    finalScoreEl.textContent = score;
    if (score > bestScore) {
        bestScore = score;
        localStorage.setItem('jumpBest', bestScore);
    }
    gameOverEl.style.display = 'block';
}

// ---------- 重开 ----------
restartBtn.addEventListener('click', () => {
    init();
});

// ---------- 绘制 ----------
function draw() {
    ctx.clearRect(0, 0, W, H);

    // ---- 背景装饰 ----
    ctx.fillStyle = '#16213e';
    ctx.fillRect(0, 0, W, H);

    // 网格
    ctx.strokeStyle = 'rgba(255,255,255,0.03)';
    ctx.lineWidth = 1;
    const gridSize = 40;
    const offsetY = -cameraY % gridSize;
    for (let x = 0; x < W; x += gridSize) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, H);
        ctx.stroke();
    }
    for (let y = offsetY; y < H; y += gridSize) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(W, y);
        ctx.stroke();
    }

    // ---- 绘制平台 ----
    for (const plat of platforms) {
        const screenY = plat.y - cameraY;
        if (screenY < -50 || screenY > H + 50) continue;

        // 平台主体
        ctx.fillStyle = plat.color;
        ctx.shadowColor = plat.color;
        ctx.shadowBlur = 12;
        ctx.beginPath();
        const r = 8;
        const x = plat.x, y = screenY, w = plat.w, h = plat.h;
        ctx.moveTo(x + r, y);
        ctx.lineTo(x + w - r, y);
        ctx.quadraticCurveTo(x + w, y, x + w, y + r);
        ctx.lineTo(x + w, y + h - r);
        ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        ctx.lineTo(x + r, y + h);
        ctx.quadraticCurveTo(x, y + h, x, y + h - r);
        ctx.lineTo(x, y + r);
        ctx.quadraticCurveTo(x, y, x + r, y);
        ctx.closePath();
        ctx.fill();

        // 高光
        ctx.shadowBlur = 0;
        ctx.fillStyle = 'rgba(255,255,255,0.15)';
        ctx.fillRect(x + 10, screenY + 3, w - 20, 4);

        // 顶部发光条
        ctx.fillStyle = 'rgba(255,255,255,0.2)';
        ctx.fillRect(x + 8, screenY + 1, w - 16, 2);
    }

    // ---- 绘制粒子 ----
    for (const p of particles) {
        const screenY = p.y - cameraY;
        ctx.globalAlpha = p.life;
        ctx.fillStyle = p.color;
        ctx.shadowBlur = 0;
        ctx.beginPath();
        ctx.arc(p.x, screenY, p.size * p.life, 0, Math.PI * 2);
        ctx.fill();
    }
    ctx.globalAlpha = 1;

    // ---- 绘制玩家 ----
    const playerScreenY = player.y - cameraY;
    ctx.shadowColor = '#e94560';
    ctx.shadowBlur = 20;

    // 身体
    const gradient = ctx.createLinearGradient(
        player.x, playerScreenY,
        player.x + player.width, playerScreenY + player.height
    );
    gradient.addColorStop(0, '#e94560');
    gradient.addColorStop(0.5, '#ff6b81');
    gradient.addColorStop(1, '#c23152');
    ctx.fillStyle = gradient;
    ctx.beginPath();
    const pr = 6;
    const px = player.x, py = playerScreenY, pw = player.width, ph = player.height;
    ctx.moveTo(px + pr, py);
    ctx.lineTo(px + pw - pr, py);
    ctx.quadraticCurveTo(px + pw, py, px + pw, py + pr);
    ctx.lineTo(px + pw, py + ph - pr);
    ctx.quadraticCurveTo(px + pw, py + ph, px + pw - pr, py + ph);
    ctx.lineTo(px + pr, py + ph);
    ctx.quadraticCurveTo(px, py + ph, px, py + ph - pr);
    ctx.lineTo(px, py + pr);
    ctx.quadraticCurveTo(px, py, px + pr, py);
    ctx.closePath();
    ctx.fill();

    // 眼睛
    ctx.shadowBlur = 0;
    ctx.fillStyle = '#fff';
    ctx.beginPath();
    ctx.arc(px + 9, py + 14, 4, 0, Math.PI * 2);
    ctx.arc(px + 21, py + 14, 4, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#1a1a2e';
    ctx.beginPath();
    ctx.arc(px + 10, py + 14, 2, 0, Math.PI * 2);
    ctx.arc(px + 22, py + 14, 2, 0, Math.PI * 2);
    ctx.fill();

    // 微笑
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(px + 15, py + 22, 5, 0.1, Math.PI - 0.1);
    ctx.stroke();

    // ---- 蓄力指示器 ----
    if (state === STATE.CHARGING) {
        const power = player.chargePower / player.maxCharge;
        ctx.shadowBlur = 0;
        ctx.fillStyle = 'rgba(255, 255, 255, 0.15)';
        ctx.fillRect(0, H - 8, W, 8);
        ctx.fillStyle = `hsl(${120 - power * 120}, 100%, ${50 + power * 30}%)`;
        ctx.fillRect(0, H - 8, W * power, 8);
        // 蓄力文字
        ctx.fillStyle = '#fff';
        ctx.font = '14px Arial';
        ctx.textAlign = 'center';
        ctx.fillText(`蓄力 ${Math.round(power * 100)}%`, W / 2, H - 20);
    }

    ctx.shadowBlur = 0;

    // ---- 最高分 ----
    ctx.fillStyle = 'rgba(255,255,255,0.2)';
    ctx.font = '12px Arial';
    ctx.textAlign = 'right';
    ctx.fillText(`最高分: ${bestScore}`, W - 15, 25);
}

// ---------- 主循环 ----------
function gameLoop() {
    update();
    draw();
    requestAnimationFrame(gameLoop);
}

// ---------- 启动 ----------
init();
gameLoop();
