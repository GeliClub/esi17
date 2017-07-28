package esi17.hli109;
import battleship.core.*;
import java.util.*;
import java.util.stream.Collectors;

/*
 * DavyJonesLocker
 * @author Nick
 */
public class DavyJonesLocker extends Ship {
    
    // Ship Testing
    public static void main(String[] args) {
        // ArrayList<String> filterTest = new ArrayList<>();
        // filterTest.add("first");
        // filterTest.add("third");
        // filterTest.add("remove");
        // filterTest.add("second");
        
        // for (String s : filterTest)
        //     System.out.println(s);
        
        // System.out.println();
        
        // filterTest = (ArrayList<String>) filterTest.stream().filter((item) -> {
        //     if (item.equals("asdf")) { 
        //         return true; // keep
        //     }
        //     return false; // remove
        // }).collect(Collectors.toList());
        
        
        // System.out.println("result: " + filterTest.size());
        
        // for (String s : filterTest)
        //     System.out.println(s);
        
    }
    
    // HashMap with the key as available movements, and value as available targets
    private HashMap<Coord, ArrayList<Ship>> actionable;
    // available target and the heat map for ALL enemy ship's attackable area
    //      If target can be sunk, the heat map will have less (ie not account for the target ship)
    //      If target cannot be sunk in one turn, heat map will include the target's reachable attacks
    //      If no target can be attacked, HashMap will have null key with value as a heatmap of all enemies reachables attacks
    private LinkedHashMap<Ship, int[][]> nextThreat;
    // The goal is to look for an area with no next threat and
    // where the actionable will have one or more targets for a given direction
    
    // Usage
    // Search for a Coord (x, y) in actionable key that is also in nextThreat's heatmap value that is of 0
    // Find the nextThreat's key that has the heatmap value of 0 and that overlaps with the Coord of actionable
    //      If there are no overlaps, move towards a 0 heatmap value in nextThreat
    //      If there are overlaps, move towards it and fire
    
    // Possible Optimization
    // nextThreat's key can be changed to an ArrayList
    // - Use case: 
    //      If current ship has enough firepower to shoot down two ships
    //      The heatmap should account for two ships being destroyed instead of one
    
    // Build the interface first...
    //  You want something that will loop over the possible actions, given the ship's stats
    //  Example: Firepower: 2, Speed: 2 
    //      Possible combinations
    //      F F M M
    //      M M F F
    //      F M F M
    //      M F M F
    //      M F F M
    //      F M M F
    
    private int count = 0;
    private int limit = 10;
    
    public DavyJonesLocker() {
        this.initializeName("Davy Jones Locker");
        this.initializeOwner("Nick");
        this.initializeHull(2);
        this.initializeFirepower(3);
        this.initializeSpeed(1);
        this.initializeRange(4);
    }
    
    private LinkedHashMap<Ship, int[][]> getThreats(Arena arena) {
        LinkedHashMap<Ship, int[][]> result = new LinkedHashMap<>();
        ArrayList<Ship> enemies = new ArrayList<>(); // all enemies
        ArrayList<Ship> reachable = new ArrayList<>(); // reachable enemies
        
        // filter yourself out of the ship list
        enemies = (ArrayList<Ship>) this.getPriorities(arena, arena.getAllShips()).stream()
            .filter((item) -> {
                if (this.equals(item)) {
                    return false;
                }
                return true;
            }).collect(Collectors.toList());
        
        if (this.getTeam() == null) { // no team play
            // Don't need to change enemies except remove yourself
            //enemies = this.getPriorities(arena, arena.getAllShips()); // TODO: filter out yourself
        }
        else { // team play
            enemies = (ArrayList<Ship>) enemies.stream() // TODO: filter out yourself
                .filter((item) -> {
                    if (this.isSameTeamAs(item)) { // check if ships are the same teams
                        return false; // remove ships that are teams
                    }
                    return true;
                }).collect(Collectors.toList());
        }
        // the enemies in this list should already have filtered out friendly fire and self fire
        reachable = (ArrayList<Ship>) enemies.stream()
            .filter((item) -> {
                if (arena.isInRange(this, item)) {
                    return true; // keep ships that are within range
                }
                return false;
            }).collect(Collectors.toList());
        
        // populate the heat map for each enemy
        if (reachable.size() == 0) {
            int[][] heatmap = new int[arena.getXSize()][arena.getYSize()];
            for (Ship enemy : enemies) {
                // int minValue = Integer.POSITIVE_INFINITY; // may not be needed, if we are getting all information, rather than predicting enemy movement and get information from that
                Coord enemyLoc = enemy.getCoord();
                int speed = enemy.getSpeed();
                int range = enemy.getRange();
                 // nested loop through the top corner of the current enemy ship's speed to the bottom corner
                for (int x = enemyLoc.getX() - speed; x <= enemyLoc.getX() + speed; x++) {
                    for (int y = enemyLoc.getY() - speed; y <= enemyLoc.getY() + speed; y++) {
                        // check coordinate against the game's boundary
                        if (x >= 0 && y >= 0 && x < arena.getXSize() && y < arena.getYSize()) {
                            // accessible via movement check
                            int xDiff = Math.abs(enemyLoc.getX() - x);
                            int yDiff = Math.abs(enemyLoc.getY() - y);
                            // check if the offset from current is within the ships speed
                            if (xDiff + yDiff <= speed) {
                                // Note: this will not account for location that is already occupied by another ship
                                // get the squared range at the location 
                                for (int rx = x - range; rx <= x + range; rx++) {
                                    for (int ry = y - range; ry <= y + range; ry++) {
                                        // incremenet the threat at that location by the firepower of the ship
                                        if (rx >= 0 && ry >= 0 && rx < arena.getXSize() && ry < arena.getYSize()) {
                                            heatmap[rx][ry] += enemy.getFirepower();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            result.put(null, heatmap);
        }
        else {
            // if there's a reachable ship then following this key's action will allow firing on a ship
            // a ship that we can attack(possibly sink) that could have a different heatmap if sunk
            for (Ship attacked : reachable) {
                int[][] heatmap = new int[arena.getXSize()][arena.getYSize()];
                for (Ship enemy : enemies) {
                    Coord enemyLoc = enemy.getCoord();
                    int speed = enemy.getSpeed();
                    int range = enemy.getRange();
                    // if attacking one of the reachable will result in the ship sinking
                    if (attacked.getHealth() <= this.getFirepower()) { 
                        // when considering the heatmap, if the enemy will sink during the attack don't add their heatmap
                        if (attacked.equals(enemy)) {
                            continue; // skip the rest of the loop
                        }
                        // otherwise the enemy will survive the attack and we will keep its heatmap 
                    }
                    // loop through the top corner of the current enemy ship's speed to the bottom corner
                    for (int x = enemyLoc.getX() - speed; x <= enemyLoc.getX() + speed; x++) {
                        for (int y = enemyLoc.getY() - speed; y <= enemyLoc.getY() + speed; y++) {
                            // check coordinate against the game's boundary
                            if (x >= 0 && y >= 0 && x < arena.getXSize() && y < arena.getYSize()) {
                                // accessible via movement check
                                int xDiff = Math.abs(enemyLoc.getX() - x);
                                int yDiff = Math.abs(enemyLoc.getY() - y);
                                if (xDiff + yDiff <= speed) {
                                    //System.out.print("("+x+","+y+")");
                                    // Note: this will not account for location that is already occupied by another ship
                                    // get the squared range at the location 
                                    for (int rx = x - range; rx <= x + range; rx++) {
                                        for (int ry = y - range; ry <= y + range; ry++) {
                                            // incremenet the threat at that location by the firepower of the ship
                                            if (rx >= 0 && ry >= 0 && rx < arena.getXSize() && ry < arena.getYSize()) {
                                                heatmap[rx][ry] += enemy.getFirepower();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    //System.out.println();
                }
                result.put(attacked, heatmap);
            }
        }
        
        return result;
    }
    
    // private HashSet<Coord> getReachableCoordRecur(Arena arena, int x, int y, int move, HashSet<Coord> res) {
    //     if (move > 0) {
    //         if (arena.getShipAt(x-1, y) == null || (this.getCoord().getX() == x && this.getCoord().getY() == y)) {
    //             res.add(new Coord(x, y));
    //         }
    //         return getReachableCoordRecur(arena, x-1, y, move-1, res);
    //         return getReachableCoordRecur(arena, x+1, y, move-1, res);
    //         return getReachableCoordRecur(arena, x, y-1, move-1, res);
    //         return getReachableCoordRecur(arena, x, y+1, move-1, res);
    //         System.out.println("("+x+","+y+")");
    //     }
    //     return res;
    // }
    
    //TODO: +2 Speed navigation, having something blocking you on one side will require moving around the object
    //      which will require more movement in order to get around 
    private HashMap<Coord, ArrayList<Ship>> getActions(Arena arena) {
        HashMap<Coord, ArrayList<Ship>> result = new HashMap<>();
        int speed = this.getSpeed();
        int range = this.getRange();
        Coord current = this.getCoord();
        ArrayList<Coord> accessible = new ArrayList<>();
        
        // nested loop through the top corner of the current ship's speed to the bottom corner
        for (int x = current.getX() - speed; x <= current.getX() + speed; x++) {
            for (int y = current.getY() - speed; y <= current.getY() + speed; y++) {
                // check coordinate against the game's boundary
                if (x >= 0 && y >= 0 && x < arena.getXSize() && y < arena.getYSize()) {
                    // accessible via movement check
                    int xDiff = Math.abs(current.getX() - x);
                    int yDiff = Math.abs(current.getY() - y);
                    // check if the offset from current is within the ships speed
                    if (xDiff + yDiff <= speed) {
                        // add coordinates that are accessible(not occupied by others) or current location(ie not moving)
                        if (arena.getShipAt(x, y) == null || this.equals(arena.getShipAt(x, y))) {
                            accessible.add(new Coord(x, y));    
                        }
                    }
                }
            }
        }
        
        // get a list of all ships, including yourself, teammates, and enemies
        ArrayList<Ship> enemies = this.getPriorities(arena, arena.getAllShips());
        
        for (int i = 0; i < accessible.size(); i++) {
            ArrayList<Ship> reachable = new ArrayList<>();
            for (Ship s : enemies) {
                // Do not include this ship as a ship that can be spotted for the given key coord movement
                if (this.equals(s)) {
                    continue;
                }
                // if ship is not an ally and within range of current ship for a given location
                if (this.getTeam() == null) { // if its not a team game
                    // System.out.println("Free-For-All");
                    if (this.checkRange(accessible.get(i), s.getCoord(), range)) {
                        reachable.add(s); // add enemy ship within a given location to a list
                    }
                }
                else { // if game play is using teams
                    // System.out.println("Teamplay");
                    if (!this.isSameTeamAs(s) && this.checkRange(accessible.get(i), s.getCoord(), range)) {
                        reachable.add(s); // add enemy ship within a given location to a list
                    }
                }
            }
            // save a accessbile coordinate and all the the ship that can be seen at said coordinate
            result.put(accessible.get(i), reachable);
        }
        return result;
    }
    
    private boolean checkRange(Coord current, Coord target, int range) {
        int xDiff = Math.abs(current.getX() - target.getX());
        int yDiff = Math.abs(current.getY() - target.getY());
        if (xDiff <= range && yDiff <= range) {
            return true;
        }
        return false;
    }
    
    private ArrayList<Ship> getPriorities(Arena arena, List<Ship> ships) {
        ships.sort((lhs, rhs) -> {
            Coord lhsCoord = lhs.getCoord();
            Coord rhsCoord = rhs.getCoord();
            Coord selfCoord = this.getCoord();
            
            double lhsDist = Math.sqrt((double) (( Math.pow((lhsCoord.getX()-selfCoord.getX()), 2) ) + Math.pow((lhsCoord.getY()-selfCoord.getY()), 2) ));
            double rhsDist = Math.sqrt((double) (( Math.pow((rhsCoord.getX()-selfCoord.getX()), 2) ) + Math.pow((rhsCoord.getY()-selfCoord.getY()), 2) ));
            double lhsStat = Math.pow(lhs.getFirepower(), 2) + Math.pow(lhs.getRange(), 2); // Values with balanced stats will be smaller
            double rhsStat = Math.pow(rhs.getFirepower(), 2) + Math.pow(rhs.getRange(), 2); // The goal is to find the minimun of these two
            
            // sort by lowest health 
            if (lhs.getHealth() != rhs.getHealth()) {
                return lhs.getHealth() - rhs.getHealth();
            }
            // sort by highest firepower/range combo, which means the lowest difference between firepower/range
            else if (lhsStat != rhsStat) {
                return (int) Math.ceil(lhsStat - rhsStat);
            }
            // // sort by fastest speed
            // else if (lhs.getSpeed() != rhs.getSpeed()) {
            //     return rhs.getSpeed() - lhs.getSpeed();
            // }
            // sort by shortest distance 
            else if (lhsDist != rhsDist) {
                return (int)Math.ceil(lhsDist - rhsDist);
            }
            // sort by name to break equalities
            else {
                return lhs.getName().compareTo(rhs.getName());
            }
        });
        return new ArrayList<Ship>(ships);
    }
    
    
    /*
     * Determines what actions the ship will take on a given turn
     * @param arena (Arena) the battlefield for the match
     * @return void
     */
    /*  Usage: getActions(arena) & getThreats(arena) 
        getActions is used first to get a finite set of coordinate keys for possible movement (getActions includes not moving as an option)
        Apply the set of accessible coordinate to the nextThreats 2D int array heatmap, and find a coordinate with a minimum value
        
    */
    
    
    /* Thoughts
        Generate the actions list
            aggregate the ArrayList values to those that are the lowest hp and close together (close together is already implemented)
            generate the threats heatmap with a key that can accept an ArrayList of Ship
            Find which action's coordinate key that will give the lowest threats
            
        Optimization:
            apply above alogrithm to different combination of move and fires
        
        Or...
            Implement getThreats in such a way where the key is an ArrayList which means those are the possible shots/retargeting
    */
    @Override
    public void doTurn(Arena arena) {
        
        // filter yourself out of the ship list
        ArrayList<Ship> enemies = (ArrayList<Ship>) this.getPriorities(arena, arena.getAllShips()).stream()
            .filter((item) -> {
                if (this.equals(item)) {
                    return false;
                }
                return true;
            }).collect(Collectors.toList());
        
        if (this.getTeam() == null) { // no team play
            // Don't need to change enemies except remove yourself
            //enemies = this.getPriorities(arena, arena.getAllShips()); // TODO: filter out yourself
        }
        else { // team play
            enemies = (ArrayList<Ship>) enemies.stream() // TODO: filter out yourself
                .filter((item) -> {
                    if (this.isSameTeamAs(item)) { // check if ships are the same teams
                        return false; // remove ships that are teams
                    }
                    return true;
                }).collect(Collectors.toList());
        }
        
        
        HashMap<Coord, ArrayList<Ship>> actions = this.getActions(arena);
        LinkedHashMap<Ship, int[][]> threats = this.getThreats(arena);
        HashMap<Coord, Double> overlap = new HashMap<>();
        
        for (Map.Entry<Coord, ArrayList<Ship>> move : actions.entrySet()) {
            Coord loc = move.getKey();
            double minThreat = Double.POSITIVE_INFINITY;
            for (Map.Entry<Ship, int[][]> fire : threats.entrySet()) {
                int[][] heatmap = fire.getValue();
                if ((double)heatmap[loc.getX()][loc.getY()] < minThreat) {
                    overlap.put(loc, minThreat);
                }
            }
        }
        
        // actions should never be empty, there will always be one entry, which is the ship's current location
        if (actions.size() == 1) {
            ArrayList<Ship> targets = this.getPriorities(arena, actions.get(new Coord(this.getCoord().getX(), this.getCoord().getY())));
            // if there are still targets and we can still shoot
            while(targets.size() > 0 && this.getRemainingShots() > 0) {
                Ship target = targets.remove(0);
                Coord fireLoc = target.getCoord();
                // pick the first ship and try to shoot until it sinks
                for (int f = target.getHealth(); f > 0; f--) {
                    this.fire(arena, fireLoc.getX(), fireLoc.getY());
                    if (this.getRemainingShots() == 0) // stop if there are no more shots
                        break;
                }
            }
        }
        // The ship have a set of movement available
        else {
            int loopLimit = 10;
            int loopCout = 0;
            while(this.getRemainingShots() > 0 && loopCout < loopLimit) {
                
                Coord minThreatCoord = null;
                Ship minThreatTarget = null;
                double minThreat = Double.POSITIVE_INFINITY;
                
                for (Map.Entry<Coord, ArrayList<Ship>> move : actions.entrySet()) {
                    Coord loc = move.getKey();
                    for (Map.Entry<Ship, int[][]> fire : threats.entrySet()) {
                        int[][] heatmap = fire.getValue();
                        if ((double)heatmap[loc.getX()][loc.getY()] < minThreat) {
                            minThreat = (double) heatmap[loc.getX()][loc.getY()];
                            minThreatTarget = fire.getKey();
                            minThreatCoord = move.getKey();
                        }
                    }
                }
                // minThreatCoord should never be null
                // if at current location
                Coord current = this.getCoord();
                if (minThreatCoord.getX() == current.getX() && minThreatCoord.getY() == current.getY()) {
                    if (minThreatTarget == null) {
                    } else {
                        for (int f = minThreatTarget.getHealth(); f > 0; f--) {
                            this.fire(arena, minThreatTarget.getCoord().getX(), minThreatTarget.getCoord().getY());
                            if (this.getRemainingShots() == 0) {
                                break;
                            }
                        }
                    }
                }
                else {
                    Direction delta = null;
                    if (minThreatTarget == null) {
                        boolean seek = true;
                        for (Map.Entry<Coord, Double> cross : overlap.entrySet()) {
                            if (cross.getValue() > 0) {
                                seek = false;
                                break;
                            }
                        }
                        minThreatCoord = enemies.get(0).getCoord();
                    }
                    
                    if (minThreatCoord.getX() > current.getX())
                        delta = Direction.EAST;
                    else if (minThreatCoord.getX() < current.getX())
                        delta = Direction.WEST;
                    else if (minThreatCoord.getY() > current.getY())
                        delta = Direction.NORTH;
                    else
                        delta = Direction.SOUTH;
                    
                    boolean useFire = true;
                    if (minThreatTarget == null) {
                        useFire = false;
                        
                    }
                    this.move(arena, delta);
                    
                    if (useFire) {
                        for (int f = minThreatTarget.getHealth(); f > 0; f--) {
                            this.fire(arena, minThreatTarget.getCoord().getX(), minThreatTarget.getCoord().getY());
                            if (this.getRemainingShots() == 0) {
                                break;
                            }
                        }                    
                    }
                }
                loopCout++;
            }
        }
        
        // Case 1: getActions return an empty <keys, values> (size == 0) -> our ship is trapped in by other ships, (stay still) (Optimization: shoot ally if movement is really needed)
        
        // Case 2: getActions return an filled key, with all empty values -> we can move, but should check threat before moving
        
        // Case 3: 
        
        // HashMap<Coord, ArrayList<Ship>> actions = this.getActions(arena);
        // LinkedHashMap<Ship, int[][]> threats = this.getThreats(arena);
        
        // Set<Coord> accessbile = actions.keySet();
        
        // for (Map.Entry<Ship, int[][]> entry : threats.entrySet()) { // print keys and values
        //     // no ship can be attacked
        //     if (entry.getKey() == null) { 
        //         System.out.println("null key, no reachable ships...");
        //     }
        //     else {
                
        //         // print the target ship
        //         //System.out.println(getShipInfo(entry.getKey()));
        //     }
        //     // print out 2D heatmap
        //     //printHeatmap(entry.getValue());
        // }
    }
    
    private String getShipInfo(Ship ship) {
        String info = "";
        info += ship.getName() + " - H: " + ship.getHealth() + " - F: " + ship.getFirepower() +" - S: " + ship.getSpeed() + " - R: " + ship.getRange() + " - ";
        Coord coord = ship.getCoord();
        info += "("+coord.getX()+", "+coord.getY()+")";
        return info;
    }
    
    private void printHeatmap(int[][] heatmap) {
        System.out.println("    00  01  02  03  04  05  06  07  08  09  10  11  12  13  14 ");
        for (int y = 0; y < heatmap[0].length; y++) {
            System.out.print(String.format("%02d ", y));
            for (int x = 0; x < heatmap.length; x++) {
                System.out.print(" " + String.format("%02d", heatmap[x][y]) + " ");
            }
            System.out.println();
        }
    }
}


/* Accessible Information

Arena
    
    isInRange(Ship a, Ship b)
    getXSize()
    getYSize()
    countLiveShips()
    getRandom()
    getTurn()

Ship
    doTurn(Arena arena)
    move(Arena arena, Direction direction)
    fire(Arena arena, int x, int y)
    getShipCoord(Arena arena, Ship ship) // for other ships in range
    getNearbyShips(Arena arena)
    
    getCoord() // for the self
    getRemainingMoves()
    getRemainingShots()
    isSunk()
    
    getHealth()
    getName()
    getOwner()
    getHull()
    getFirepower()
    getSpeed()
    getRange()
    
Coord
    getX()
    getY()
    
Direction
    NORTH, SOUTH, WEST, EAST
    
Grid

Helper

*/

/* actionable testing
    @Override
    public void doTurn(Arena arena) {
        Coord thisShip = this.getCoord();
        System.out.println("start at " + thisShip.getX() + ", " + thisShip.getY());
        HashMap<Coord, ArrayList<Ship>> actions = getActions(arena);
        for (Map.Entry<Coord, ArrayList<Ship>> entry : actions.entrySet()) { // print keys and values
            if (entry.getKey() == null) {
                System.out.println("null key");
            }
            else {
                // print an accessible coordinate
                System.out.print("Location " + entry.getKey().getX() + ", " + entry.getKey().getY() +": ");
            }
            // print attackable ships at the given location
            for (Ship s : entry.getValue()) {
                Coord c = s.getCoord();
                System.out.print(s.getName() + " (" + c.getX() + ", " + c.getY() + "), ");
            }
            System.out.println();
        }
        System.out.println();
        this.move(arena, Direction.EAST);
    }
*/

/* nextThreat testing
    @Override
    public void doTurn(Arena arena) {
        Coord thisShip = this.getCoord();
        System.out.println("start at " + thisShip.getX() + ", " + thisShip.getY());
        LinkedHashMap<Ship, int[][]> test = this.getThreats(arena);
        // iterate map using entryset in for loop
        for (Map.Entry<Ship, int[][]> entry : test.entrySet()) { // print keys and values
            if (entry.getKey() == null) {
                System.out.println("null key, no reachable ships...");
            }
            else {
                // print the target ship
                System.out.println(getShipInfo(entry.getKey()));
            }
            // print out 2D heatmap
            printHeatmap(entry.getValue());
        }
        
        System.out.println("Movement Started");
        Direction[] dir = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        int rand = arena.getRandom().nextInt(4);
        this.move(arena, dir[3]);
        this.move(arena, dir[3]);
        this.move(arena, dir[3]);
        System.out.println("Movement Ended");
        
        thisShip = this.getCoord();
        System.out.println("end at " + thisShip.getX() + ", " + thisShip.getY());
        test = this.getThreats(arena);
        // iterate map using entryset in for loop
        for (Map.Entry<Ship, int[][]> entry : test.entrySet()) { // print keys and values
            if (entry.getKey() == null) {
                System.out.println("null key, no reachable ships...");
            }
            else {
                // print the target ship
                System.out.println(getShipInfo(entry.getKey()));
            }
            // print out 2D heatmap
            printHeatmap(entry.getValue());
        }
        
        // System.out.println();
        System.out.println();
    }
*/