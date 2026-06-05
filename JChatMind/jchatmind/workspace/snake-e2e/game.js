/* ===== 贪吃蛇游戏 - 核心逻辑 ===== */

(function () {
  'use strict';

  // ========== 配置常量 ==========
  const BOARD_SIZE = 20;          // 20×20 网格
  const CELL_SIZE = 25;           // 像素
  const TICK_BASE = 150;          // 基础毫秒/帧

  // ========== DOM 引用 ==========
  const canvas = document.getElementById('gameCanvas');
  const ctx = canvas.getContext('2d');
  const scoreSpan = document.getElementById('score');
  const highScoreSpan = document.getElementById('highScore');
  const restartBtn = document.getElementById('restartBtn');
  const gameOverOverlay = document.getElementById('gameOverOverlay');
  const finalScoreSpan = document.getElementById('finalScore');
  const overlayRestartBtn = document.getElementById('overlayRestartBtn');

  canvas.width = BOARD_SIZE * CELL_SIZE;
  canvas.height = BOARD_SIZE * CELL_SIZE;

  // ========== 游戏状态 ==========
  let snake = [];           // 坐标数组 [{x, y}, ...]
  let food = { x: 0, y: 0 };
  let direction = 'RIGHT';  // 当前行进方向
  let nextDirection = 'RIGHT';
  let score = 0;
  let highScore = parseInt(localStorage.getItem('snakeHighScore')) || 0;
  let gameOver = false;
  let isRunning = false;
  let animationId = null;
  let lastTimestamp = 0;
  let tickAccumulator = 0;

  // ========== 初始化 ==========
  function init() {
    // 蛇：长度为 3，水平居中
    const midY = Math.floor(BOARD_SIZE / 2);
    snake = [
      { x: 3, y: midY },
      { x: 2, y: midY },
      { x: 1, y: midY },
    ];
    direction = 'RIGHT';
    nextDirection = 'RIGHT';
    score = 0;
    gameOver = false;
    isRunning = true;
    tickAccumulator = 0;
    updateScore();
    placeFood();
    hideOverlay();
    draw();
  }

  // ========== 食物生成 ==========
  function placeFood() {
    const total = BOARD_SIZE * BOARD_SIZE;
    if (snake.length >= total) {
      // 胜利（蛇占满整个面板）
      gameOver = true;
      showOverlay('You Win! 🎉');
      return;
    }
    const snakeSet = new Set(snake.map(c => `${c.x},${c.y}`));
    let freeCells = [];
    for (let i = 0; i < BOARD_SIZE; i++) {
      for (let j = 0; j < BOARD_SIZE; j++) {
        if (!snakeSet.has(`${i},${j}`)) freeCells.push({ x: i, y: j });
      }
    }
    if (freeCells.length === 0) {
      gameOver = true;
      showOverlay('You Win! 🎉');
      return;
    }
    const idx = Math.floor(Math.random() * freeCells.length);
    food = freeCells[idx];
  }

  // ========== 游戏逻辑更新 ==========
  function update() {
    if (gameOver || !isRunning) return;

    // 应用下一个合法方向（不可反向）
    const opposite = {
      'UP': 'DOWN', 'DOWN': 'UP', 'LEFT': 'RIGHT', 'RIGHT': 'LEFT'
    };
    if (nextDirection && opposite[nextDirection] !== direction) {
      direction = nextDirection;
    }

    // 计算新蛇头
    const head = snake[0];
    let newHead = { ...head };
    switch (direction) {
      case 'RIGHT': newHead.x += 1; break;
      case 'LEFT':  newHead.x -= 1; break;
      case 'UP':    newHead.y -= 1; break;
      case 'DOWN':  newHead.y += 1; break;
      default: break;
    }

    // 碰墙检测
    if (newHead.x < 0 || newHead.x >= BOARD_SIZE ||
        newHead.y < 0 || newHead.y >= BOARD_SIZE) {
      endGame();
      return;
    }

    // 碰自身检测（旧蛇尾会在移动后移除，暂不考虑）
    // 先判断是否吃到食物
    const ate = (newHead.x === food.x && newHead.y === food.y);

    // 组装新蛇
    let newSnake = [newHead, ...snake];
    if (!ate) {
      newSnake.pop(); // 没吃到 → 移除尾部
    }

    // 碰自身检测（检查新蛇头是否与新蛇身其他部分重叠）
    const headStr = `${newHead.x},${newHead.y}`;
    const bodyPart = newSnake.slice(1).map(c => `${c.x},${c.y}`);
    if (bodyPart.includes(headStr)) {
      endGame();
      return;
    }

    snake = newSnake;

    if (ate) {
      score++;
      updateScore();
      placeFood();
      if (gameOver) return; // placeFood 可能在满盘时触发胜利
    }
  }

  // ========== 绘制 ==========
  function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // === 网格线（浅暗线） ===
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.04)';
    ctx.lineWidth = 0.5;
    for (let i = 0; i <= BOARD_SIZE; i++) {
      ctx.beginPath();
      ctx.moveTo(i * CELL_SIZE, 0);
      ctx.lineTo(i * CELL_SIZE, canvas.height);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(0, i * CELL_SIZE);
      ctx.lineTo(canvas.width, i * CELL_SIZE);
      ctx.stroke();
    }

    // === 蛇身 ===
    for (let i = 0; i < snake.length; i++) {
      const seg = snake[i];
      const x = seg.x * CELL_SIZE;
      const y = seg.y * CELL_SIZE;
      const padding = i === 0 ? 1 : 2;

      if (i === 0) {
        // 蛇头
        const gradient = ctx.createRadialGradient(
          x + CELL_SIZE / 2, y + CELL_SIZE / 2, 2,
          x + CELL_SIZE / 2, y + CELL_SIZE / 2, CELL_SIZE / 2
        );
        gradient.addColorStop(0, '#58a6ff');
        gradient.addColorStop(1, '#1f6feb');
        ctx.fillStyle = gradient;
      } else {
        // 蛇身渐变：从深绿到亮绿
        const ratio = 1 - (i / snake.length) * 0.5;
        const g = Math.round(180 * ratio + 60);
        ctx.fillStyle = `rgb(40, ${g}, 40)`;
      }

      ctx.shadowColor = i === 0 ? 'rgba(88, 166, 255, 0.4)' : 'rgba(50, 205, 50, 0.2)';
      ctx.shadowBlur = i === 0 ? 10 : 6;

      ctx.beginPath();
      ctx.roundRect(x + padding, y + padding, CELL_SIZE - padding * 2, CELL_SIZE - padding * 2, 4);
      ctx.fill();
    }

    // 重置阴影
    ctx.shadowColor = 'transparent';
    ctx.shadowBlur = 0;

    // === 食物 ===
    const fx = food.x * CELL_SIZE;
    const fy = food.y * CELL_SIZE;
    const cx = fx + CELL_SIZE / 2;
    const cy = fy + CELL_SIZE / 2;
    const radius = CELL_SIZE / 2 - 2;

    // 发光效果
    ctx.shadowColor = 'rgba(255, 55, 95, 0.6)';
    ctx.shadowBlur = 12;

    const grd = ctx.createRadialGradient(cx - 3, cy - 3, 1, cx, cy, radius);
    grd.addColorStop(0, '#ff6b81');
    grd.addColorStop(0.6, '#ff4757');
    grd.addColorStop(1, '#c0392b');
    ctx.fillStyle = grd;
    ctx.beginPath();
    ctx.arc(cx, cy, radius, 0, Math.PI * 2);
    ctx.fill();

    // 高光
    ctx.shadowBlur = 0;
    ctx.fillStyle = 'rgba(255, 255, 255, 0.35)';
    ctx.beginPath();
    ctx.arc(cx - 4, cy - 4, 4, 0, Math.PI * 2);
    ctx.fill();
  }

  // ========== 游戏结束 ==========
  function endGame() {
    if (gameOver) return;
    gameOver = true;
    isRunning = false;
    if (score > highScore) {
      highScore = score;
      localStorage.setItem('snakeHighScore', highScore);
    }
    updateScore();
    showOverlay('Game Over');
  }

  function showOverlay(title) {
    document.querySelector('.overlay-title').textContent = title;
    finalScoreSpan.textContent = score;
    gameOverOverlay.classList.add('active');
  }

  function hideOverlay() {
    gameOverOverlay.classList.remove('active');
  }

  // ========== UI 更新 ==========
  function updateScore() {
    scoreSpan.textContent = score;
    highScoreSpan.textContent = highScore;
  }

  // ========== 动画循环 ==========
  function gameLoop(timestamp) {
    if (!isRunning) {
      animationId = requestAnimationFrame(gameLoop);
      return;
    }

    const delta = timestamp - lastTimestamp;
    lastTimestamp = timestamp;
    tickAccumulator += delta;

    // 固定时间步更新
    const tickInterval = TICK_BASE;
    while (tickAccumulator >= tickInterval) {
      update();
      tickAccumulator -= tickInterval;
      if (gameOver) break;
    }

    draw();

    animationId = requestAnimationFrame(gameLoop);
  }

  // ========== 键盘控制 ==========
  function handleKey(e) {
    const key = e.key;
    // 阻止页面滚动
    if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(key)) {
      e.preventDefault();
    }

    if (gameOver) return;

    const map = {
      'ArrowUp': 'UP',
      'ArrowDown': 'DOWN',
      'ArrowLeft': 'LEFT',
      'ArrowRight': 'RIGHT'
    };
    const newDir = map[key];
    if (newDir) {
      // 在 update 中会做反向过滤，这里直接设置即可
      nextDirection = newDir;
    }
  }

  // ========== 重启 ==========
  function restart() {
    if (animationId) cancelAnimationFrame(animationId);
    init();
    lastTimestamp = performance.now();
    tickAccumulator = 0;
    animationId = requestAnimationFrame(gameLoop);
  }

  // ========== 绑定事件 ==========
  window.addEventListener('keydown', handleKey);
  restartBtn.addEventListener('click', restart);
  overlayRestartBtn.addEventListener('click', restart);

  // ========== roundRect polyfill（为不支持浏览器提供） ==========
  if (!CanvasRenderingContext2D.prototype.roundRect) {
    CanvasRenderingContext2D.prototype.roundRect = function (x, y, w, h, r) {
      if (r > w / 2) r = w / 2;
      if (r > h / 2) r = h / 2;
      this.moveTo(x + r, y);
      this.arcTo(x + w, y, x + w, y + h, r);
      this.arcTo(x + w, y + h, x, y + h, r);
      this.arcTo(x, y + h, x, y, r);
      this.arcTo(x, y, x + w, y, r);
      this.closePath();
      return this;
    };
  }

  // ========== 启动游戏 ==========
  init();
  lastTimestamp = performance.now();
  animationId = requestAnimationFrame(gameLoop);
})();
