package client;

import client.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by seyedparsa on 3/8/2018 AD.
 */
public class Defence {
    private static double footprintRecall = 0.9;
    private static double infectionFactor = 0.3;
    private Double[][] footprint;
    private Double[][] seen;
    private Double[][] heat;
    private Map map;
    private int H, W;
    private List<Tower> myArchers, myCannons;
    private Tower[][] myTowers;



    Defence(World game){
        map = game.getDefenceMap();
        H = map.getHeight();
        W = map.getWidth();
        footprint = new Double[H][W];
        seen = new Double[H][W];
        heat = new Double[H][W];
        myArchers = new ArrayList<>();
        myCannons = new ArrayList<>();
        myTowers = new Tower[H][W];
        for (Path path : game.getDefenceMapPaths()){
            for (Cell cell : path.getRoad()){
                int x = cell.getLocation().getX(), y = cell.getLocation().getY();
                if (footprint[y][x] == null) footprint[y][x] = 0d;
                footprint[y][x] += (1-footprintRecall);
                heat[y][x] = 0d;
            }
        }
        for (Cell cell : map.getCellsList())
            if (game.isTowerConstructable(cell))
                seen[cell.getLocation().getY()][cell.getLocation().getX()] = 0d;
        myArchers = new ArrayList();
        myCannons = new ArrayList();
    }

    private void distribute(){
        /*for (int i = 0; i < h; i++)
            for (int j = 0; j < w; j++){
                if (res[i][j] == null) continue;
                backup[i][j] = res[i][j];
                res[i][j] = 0d;
            }

        for (int i = 0; i < h; i++)
            for (int j = 0; j < w; j++){
                if (res[i][j] == null) continue;
                int cnt = 0;
                double sum = 0;
                for (int ii = -1; ii <= 1; ii++)
                    for (int jj = -1; jj <= 1; jj++){
                        int y = i + ii, x = j + jj;
                        if (x >= 0 && x < w && y >= 0 && y < h && backup[y][x] != null){
                            sum += backup[y][x];
                            cnt++;
                        }
                    }
                if (cnt == 0){
                    res[i][j] = backup[i][j];
                }
                else
                    res[i][j] = (backup[i][j]*alpha + sum/cnt*(1-alpha));
            }*/
    }

    private void computeSeen(){
        for (int i = 0; i < H; i++)
            for (int j = 0; j < W; j++)
                if (seen[i][j] != null) {
                    seen[i][j] = 0d;
                    for (int ii = -2; ii <= 2; ii++)
                        for (int jj = -2; jj <= 2; jj++) {
                            int y = i + ii, x = j + jj;
                            if (Math.abs(ii) + Math.abs(jj) <= 2 && x >= 0 && x < W && y >= 0 && y < H && footprint[y][x] != null)
                                seen[i][j] += footprint[y][x];
                        }
                }
    }

    private void computeStep(World game){
        for (Unit unit : game.getEnemyUnits()) {
            int x = unit.getLocation().getX(), y = unit.getLocation().getY();
            int w = (unit instanceof LightUnit ? LightUnit.INITIAL_HEALTH : HeavyUnit.INITIAL_HEALTH);
            heat[y][x] += w;
        }

        for (int i = 0; i < H; i++)
            for (int j = 0; j < W; j++)
                if (footprint[i][j] != null)
                    footprint[i][j] = (footprint[i][j]/(1-footprintRecall)*footprintRecall + heat[i][j]) * (1-footprintRecall);
    }

    private void createTower(World game, int money){
        int w = map.getWidth(), h = map.getHeight();
        int x = -1, y = -1;
        double bst = 0d;
        for (int i = 0; i < h; i++)
            for (int j = 0; j < w; j++) {
                double cur = 0;
                if (myTowers[i][j] != null || game.isTowerConstructable(map.getCellsGrid()[i][j])){
                    int lvl = (myTowers[i][j] == null ? 0 : myTowers[i][j].getLevel());
                    cur = seen[i][j] - lvl*100000;
                }
                if (x == -1 || cur > bst) {
                    bst = cur;
                    x = j;
                    y = i;
                }
            }
        if (x != -1 && seen[y][x] > 1e-7) {
            int lvl = (myTowers[y][x] == null ? 0 : myTowers[y][x].getLevel());
            int prc = (myTowers[y][x] == null ? archerPrice() : archerLevelUpPrice(lvl));
            if (prc <= money) {
                System.out.println("Let's create a tower at " + x + "," + y + "(" + (lvl+1) + "," + prc + ")" + " because of " + seen[y][x]);
                if (myTowers[y][x] == null)
                    game.createArcherTower(1, x, y);
                else
                    game.upgradeTower(myTowers[y][x]);
            }
            else
                System.out.println("Pool nakafi: " + money + " " + prc);
        }
        else
            System.out.println((x == -1 ? "Not Found Error" : "Epsilon Error"));
    }

    public void resist(World game, int money){
        map = game.getDefenceMap();
        for (int i = 0; i < H; i++)
            for (int j = 0; j < W; j++) {
                if (heat[i][j] != null)
                    heat[i][j] = 0d;
                myTowers[i][j] = null;
            }
        myArchers.clear();
        myCannons.clear();
        for (Tower tower : game.getMyTowers()) {
            myTowers[tower.getLocation().getY()][tower.getLocation().getX()] = tower;
            if (tower instanceof ArcherTower) myArchers.add(tower);
            if (tower instanceof CannonTower) myCannons.add(tower);
        }
        computeStep(game);
        distribute();
        computeSeen();
        createTower(game, money);
    }

    int archerPrice(){
        return ArcherTower.INITIAL_PRICE + myArchers.size()*ArcherTower.INITIAL_PRICE_INCREASE;
    }

    int archerDamage(int lvl){
        double res = ArcherTower.INITIAL_DAMAGE;
        for (int i = 0; i < lvl-1; i++)
            res *= 1.4;
        return (int)res;
    }

    int archerLevelUpPrice(int lvl){
        double res = ArcherTower.INITIAL_LEVEL_UP_PRICE;
        for (int i = 0; i < lvl-1; i++)
            res *= 1.5;
        return (int)res;
    }

    int cannonPrice(){
        return CannonTower.INITIAL_PRICE + myCannons.size()*CannonTower.INITIAL_PRICE_INCREASE;
    }

}
