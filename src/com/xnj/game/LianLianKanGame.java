package com.xnj.game;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/**
 * 连连看游戏
 * <p>
 * 优先看init方法
 * </p>
 * 
 * @author w24882 xieningjie
 * @date 2020年1月21日
 * @version v1.0.0
 */
public class LianLianKanGame extends JFrame implements ActionListener {

    /**
     * 
     */
    private static final long serialVersionUID = 8348741389417696897L;

    JPanel north = new JPanel();
    JPanel center = new JPanel();
    JPanel south = new JPanel();
    final static int COL = 5;
    final static int ROW = 6;
    GameCard[][] dataGrid;

    JProgressBar progressBar;
    JLabel scoreLabel;
    JLabel msgLabel;

    JButton firstClickBtn = null;
    JButton secondClickBtn = null;

    JButton resetBtn = new JButton("重列");
    JButton regameBtn = new JButton("再玩一次");
    JButton exitBtn = new JButton("退出");
    int clickTimes;
    int x0, y0, x1, y1;
    int[][] dirs = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };
    LinkedList<GamePoint> eliminateStack = new LinkedList<>();
    File logFile = new File("board.log");
    BufferedWriter bufferedWriter;
    int score = 0; // 分数
    int scoreDelta = 100; // 连击递增分数值
    int doubleHitCount = 0; // 连击数
    int totalCard; // 总共卡片数量
    int totalTime = 60000; // 总共时间
    int bonus = totalTime / 2; // 奖励时间
    int remainTime = totalTime; // 剩余时间
    long lastHitTime = 0; // 最后一次连击时间
    boolean terminate = false; // 停止标识
    boolean paused = false; // 暂停标识
    Thread progressBarThread; // 进度条线程

    public LianLianKanGame() {
        super("连连看");
    }

    private void init() {
        initFrastructure(); // 初始化结构
        initProgressBar(); // 初始化时间进度条
        initScore(); // 初始化分数
        initMsg(); // 舒适化消息
        initDataGrid(); // 初始化数据
        initButton(); // 初始化按钮
        initSouthFunBtn(); // 初始化功能按钮
        this.setVisible(true);
        printLog("===开始游戏===");
    }

    private void initFrastructure() {
        this.setLayout(new BorderLayout());
        this.setBounds(400, 200, 600, 500);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        north = new JPanel();
        center = new JPanel();
        south = new JPanel();
        this.add(north, BorderLayout.NORTH);
        this.add(center, BorderLayout.CENTER);
        this.add(south, BorderLayout.SOUTH);
        center.setLayout(new GridLayout(ROW, COL));
    }

    private void initSouthFunBtn() {
        south.add(resetBtn);
        south.add(regameBtn);
        south.add(exitBtn);
        resetBtn.addActionListener(this);
        regameBtn.addActionListener(this);
        exitBtn.addActionListener(this);
    }

    public JProgressBar getProgressBar() {
        return this.progressBar;
    }

    private void initProgressBar() {
        if (null == progressBar) { // 如果是再玩一次就不用再新增
            progressBar = new JProgressBar();
            progressBar.setValue(100);
            progressBar.setStringPainted(true);
            north.add(progressBar);
        } else {
            north.remove(progressBar);
            progressBar = new JProgressBar();
            progressBar.setValue(100);
            progressBar.setStringPainted(true);
            north.add(progressBar);
        }
        remainTime = totalTime;

        if (null == progressBarThread) {
            progressBarThread = new Thread() {
                public void run() {
                    while (!terminate) {
                        if (paused) {
                            continue;
                        }
                        try {
                            remainTime -= 1000;
                            getProgressBar().setValue((int) ((remainTime + 0.0) / totalTime * 100));
                            if (remainTime < 0) {
                                paused = true;
                                setMsg("游戏结束！！");
                                JOptionPane.showMessageDialog(null, "游戏结束！！", "游戏结束！！", JOptionPane.ERROR_MESSAGE);
                                recordScore();
                            }
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            progressBarThread.start();
        }
    }

    private void initMsg() {
        if (null != msgLabel) {
            north.remove(msgLabel);
        }
        msgLabel = new JLabel();
        north.add(msgLabel);
    }

    private void initScore() {
        if (null != scoreLabel) {
            north.remove(scoreLabel);
        }
        scoreLabel = new JLabel();
        north.add(scoreLabel);
        setScore();
    }

    private void setScore() {
        scoreLabel.setText("分数：" + score);
    }

    private void setMsg(String msg) {
        msgLabel.setText(msg);
    }

    private void initButton() {
        ArrayList<JButton> buttons = prepareButton();
        adapterButton(buttons);
    }

    private void adapterButton(ArrayList<JButton> buttons) {
        this.center.removeAll();
        for (JButton button : buttons) {
            center.add(button);
        }
    }

    private ArrayList<JButton> prepareButton() {
        ArrayList<JButton> buttons = new ArrayList<>();
        for (int i = 1; i < this.dataGrid.length - 1; ++i) {
            for (int j = 1; j < this.dataGrid[0].length - 1; ++j) {
                final int value = this.dataGrid[i][j].value * 16;
                JButton button = new JButton(value + "");
                if (value == 0) {
                    button.setVisible(false);
                }
                Color c = new Color(value, 200, 128);
                button.setBackground(c);
                button.addActionListener(this);
                buttons.add(button);
            }
        }
        return buttons;
    }

    private void initDataGrid() {
        dataGrid = new GameCard[ROW + 2][COL + 2];
        Random r = new Random();
        for (int i = 0; i < ROW + 2; ++i) {
            for (int j = 0; j < COL + 2; ++j) {
                dataGrid[i][j] = new GameCard();
            }
        }
        totalCard = ROW * COL;

        for (int i = 0; i < ROW * COL / 2; i++) {
            int x = 0;
            int y = 0;
            int gray = r.nextInt(15) + 1;
            for (int time = 0; time < 2; ++time) {
                do {
                    x = r.nextInt(ROW) + 1;
                    y = r.nextInt(COL) + 1;
                } while (dataGrid[x][y].value != 0);
                dataGrid[x][y].value = gray;
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JButton source = (JButton) e.getSource();
        if (source == exitBtn) {
            resetClick();
            exit();
        } else if (source == resetBtn) {
            if (paused) {
                JOptionPane.showMessageDialog(null, "游戏结束！！", "游戏结束！！", JOptionPane.ERROR_MESSAGE);
                return;
            }
            resetClick();
            reset();
        } else if (source == regameBtn) {
            resetClick();
            regame();
        } else {
            if (paused) {
                JOptionPane.showMessageDialog(null, "游戏结束！！", "游戏结束！！", JOptionPane.ERROR_MESSAGE);
                return;
            }
            clickTimes = (clickTimes + 1) % 2;
            if (clickTimes == 0) {
                outer: for (int i = 1; i <= ROW; ++i) {
                    for (int j = 1; j <= COL; ++j) {
                        if (source == center.getComponent((i - 1) * COL + j - 1)) {
                            x1 = i;
                            y1 = j;
                            break outer;
                        }
                    }
                }
                secondClickBtn = source;
                if (secondClickBtn == firstClickBtn) {
                    resetClick();
                    return;
                }
                if (firstClickBtn.getText().equals(secondClickBtn.getText())) {
                    judge();
                }
                resetClick();
                x0 = y0 = x1 = y1 = 0;
            } else { // clickTimes == 1
                firstClickBtn = source;
                outer: for (int i = 1; i <= ROW; ++i) {
                    for (int j = 1; j <= COL; ++j) {
                        if (source == center.getComponent((i - 1) * COL + j - 1)) {
                            x0 = i;
                            y0 = j;
                            break outer;
                        }
                    }
                }
            }
        }
    }

    private void resetClick() {
        firstClickBtn = null;
        secondClickBtn = null;
        clickTimes = 0;
    }

    private void judge() {
        if (colAlign() || rowAlign()) { // 判断相邻
            remove();
            return;
        } else if (matchConOne()) { // 判断两个按钮是否存在只拐两次的路径
            remove();
            return;
        }

    }

    /**
     * <pre>
     * C _ _ _
     * A G S C
     * 
     * C G S A
     * _ _ _ C
     * 
     * _ _ _ C
     * C G S A
     * 
     * A G S C
     * C _ _ _
     * 
     * A S D F
     * _ _ _ _
     * C E G C
     * </pre>
     * 
     * @return
     * @author w24882 xieningjie
     * @date 2020年1月20日
     */
    private boolean matchConOne() {
        int changeTime = 0;
        boolean[][] visited = new boolean[ROW + 2][COL + 2];
        visited[x0][y0] = true;

        for (int[] dir : dirs) { // 从4个方向搜索路径
            int nextX = x0 + dir[0];
            int nextY = y0 + dir[1];
            eliminateStack.clear();
            eliminateStack.push(new GamePoint(x0, y0));
            if (recursion(changeTime, visited, dir, nextX, nextY)) {
                return true;
            }
        }
        return false;
    }

    private boolean recursion(int changeTime, boolean[][] visited, int[] dir, int x, int y) {
        if (x1 == x && y1 == y) {
            return true;
        }
        if (changeTime > 2) { // 最多拐两次弯
            return false;
        }
        if (boundValid(x, y) && dataGrid[x][y].value == 0 && !visited[x][y]) { // 检测路线是否碰到边界，是否碰到障碍，是否已经走过
            visited[x][y] = true; // 走过的路径标记一下
            for (int[] d : dirs) {
                int nextX = x + d[0]; // 按四个方向继续搜索
                int nextY = y + d[1];
                eliminateStack.push(new GamePoint(nextX, nextY, d != dir));
                if (recursion(d != dir ? changeTime + 1 : changeTime, visited, d, nextX, nextY)) { // 递归寻找路径
                    return true;
                }
                eliminateStack.pop();
            }
            visited[x][y] = false; // 回溯
        }
        return false;
    }

    /**
     * 检查越界
     * 
     * @param nextX 下一步X
     * @param nextY 下一步Y
     * @return
     * @author w24882 xieningjie
     * @date 2020年1月20日
     */
    private boolean boundValid(int nextX, int nextY) {
        return nextX >= 0 && nextX < ROW + 2 && nextY >= 0 && nextY < COL + 2;
    }

    private void remove() {
        firstClickBtn.setVisible(false);
        dataGrid[x0][y0].value = 0;

        secondClickBtn.setVisible(false);
        dataGrid[x1][y1].value = 0;

        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastHitTime < 1500) {
            doubleHitCount++;
            setMsg(doubleHitCount + "连击！！");
        } else {
            doubleHitCount = 1;
            setMsg("加油~");
        }
        lastHitTime = currentTimeMillis;

        score += scoreDelta * doubleHitCount;
        setScore();

        totalCard -= 2;
        if (totalCard == 0) {
            JOptionPane.showMessageDialog(null, "恭喜过关！！ 奖励时间：" + bonus / 1000 + "s", "恭喜过关！！",
                    JOptionPane.INFORMATION_MESSAGE);
            // recordScore();
            center.setVisible(false);
            initDataGrid();
            ArrayList<JButton> buttons = prepareButton();
            adapterButton(buttons);
            center.setVisible(true);
            remainTime += bonus;
        }

        printRemoveLog();
        genEffect();
    }

    private void recordScore() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            String ip = addr.getHostAddress().toString(); // 获取本机ip
            String hostName = addr.getHostName().toString(); // 获取本机计算机名称

            BufferedWriter bw = new BufferedWriter(new FileWriter(new File("history.txt"), true));
            bw.append("===========================================");
            bw.newLine();
            bw.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            bw.newLine();
            bw.append("用户：" + hostName + ", " + ip);
            bw.newLine();
            bw.append("分数：" + score);
            bw.newLine();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 生成特效
     * 
     * @author w24882 xieningjie
     * @date 2020年1月21日
     */
    private void genEffect() {

    }

    private void printRemoveLog() {
        System.out.println("消除(" + x0 + "," + y0 + "), (" + x1 + "," + y1 + ")");
        StringBuilder sb = new StringBuilder();
        for (Point p : eliminateStack) {
            sb.append("(" + p.x + "," + p.y + ") => ");
        }
        if (eliminateStack.size() > 0) {
            System.out.println(sb.substring(0, sb.lastIndexOf("=>")));
        }

        StringBuilder sb1 = new StringBuilder();
        sb1.append("消除(" + x0 + "," + y0 + "), (" + x1 + "," + y1 + ")");
        sb1.append(System.lineSeparator());
        for (int i = 1; i <= ROW; ++i) {
            for (int j = 1; j <= COL; ++j) {
                sb1.append(dataGrid[i][j].value + "\t");
            }
            sb1.append(System.lineSeparator());
        }
        printLog(sb1.toString());
    }

    /**
     * 打印实时棋盘
     * 
     * @author w24882 xieningjie
     * @date 2020年1月21日
     */
    private void printLog(String msg) {
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(logFile, true));
            bufferedWriter.append("===============================");
            bufferedWriter.newLine();
            bufferedWriter.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            bufferedWriter.newLine();
            bufferedWriter.append(msg);
            bufferedWriter.newLine();
            bufferedWriter.append("===============================");
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean rowAlign() {
        return y0 == y1 && (x0 == x1 - 1 || x0 == x1 + 1);
    }

    private boolean colAlign() {
        return x0 == x1 && (y0 == y1 - 1 || y0 == y1 + 1);
    }

    private void shuffleDataGrid() {
        GameCard[][] tmp = new GameCard[ROW + 2][COL + 2];
        for (int i = 0; i < ROW + 2; ++i) {
            for (int j = 0; j < COL + 2; ++j) {
                tmp[i][j] = new GameCard();
                tmp[i][j].value = dataGrid[i][j].value;
            }
        }

        for (int i = 0; i < ROW + 2; ++i) {
            for (int j = 0; j < COL + 2; ++j) {
                dataGrid[i][j] = new GameCard();
            }
        }

        Random r = new Random();
        for (int i = 1; i <= ROW; ++i) {
            for (int j = 1; j <= COL; ++j) {
                int x, y;
                do {
                    x = r.nextInt(ROW) + 1;
                    y = r.nextInt(COL) + 1;
                } while (dataGrid[x][y].value != 0);
                dataGrid[x][y].value = tmp[i][j].value;
            }
        }
    }

    private void regame() {
        System.out.println("===重新开始游戏===");
        printLog("===重新开始游戏===");
        center.setVisible(false);
        initProgressBar();
        initScore();
        initMsg();

        initDataGrid();
        shuffleDataGrid();
        ArrayList<JButton> buttons = prepareButton();
        adapterButton(buttons);
        paused = false;
        center.setVisible(true);
    }

    private void reset() {
        System.out.println("======重列======");
        printLog("======重列======");
        center.setVisible(false);
        shuffleDataGrid();
        ArrayList<JButton> buttons = prepareButton();
        adapterButton(buttons);
        center.setVisible(true);
    }

    private void exit() {
        printLog("======退出游戏======");
        recordScore();
        System.exit(0);
    }

    public static void main(String[] args) {
        LianLianKanGame lianLianKanGame = new LianLianKanGame();
        lianLianKanGame.init();
    }

    class GameCard {
        int value;
        boolean isBoard = false;
    }

    class GamePoint extends Point {

        /**
         * 
         */
        private static final long serialVersionUID = 171105494012618643L;
        /**
         * 是否为拐角
         */
        boolean isCorner;

        public GamePoint(int x, int y) {
            super(x, y);
        }

        public GamePoint(int x, int y, boolean isCorner) {
            super(x, y);
            this.isCorner = isCorner;
        }

    }
}
