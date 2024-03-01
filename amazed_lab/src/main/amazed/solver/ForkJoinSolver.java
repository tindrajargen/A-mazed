package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */


public class ForkJoinSolver
    extends SequentialSolver
{
    
    private static AtomicBoolean foundgoal = new AtomicBoolean();   
    public int player;
    private List<ForkJoinSolver> solvers = new ArrayList<ForkJoinSolver>();
    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze   the maze to be searched
     */
    public ForkJoinSolver(Maze maze)
    {
        super(maze);
        
        
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze        the maze to be searched
     * @param forkAfter   the number of steps (visited nodes) after
     *                    which a parallel task is forked; if
     *                    <code>forkAfter &lt;= 0</code> the solver never
     *                    forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter)
    {
        this(maze);
        this.forkAfter = forkAfter;  
        this.visited = new ConcurrentSkipListSet<>();
         
    }

    
    public ForkJoinSolver(Maze maze, int forkAfter, int start, Set<Integer> visited){
        this(maze, forkAfter);
        this.start = start;
        this.visited = visited;
        
    }

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return   the list of node identifiers from the start node to a
     *           goal node in the maze; <code>null</code> if such a path cannot
     *           be found.
     */
    @Override
    public List<Integer> compute()
    {
        return parallelSearch();
    }

    private List<Integer> parallelSearch()
    {
         // one player active on the maze at start
         
         int player = maze.newPlayer(start);
         // keeps track of the steps, so that it does not fork all the time
         int step = 0;
         // start with start node
         frontier.push(start);
         // as long as not all nodes have been processed and the goal is not found
         while (!frontier.empty() && !foundgoal.get()) {
            
             // get the new node to process
            int current = frontier.pop(); 
             // checks if the new node has the goal
            if(maze.hasGoal(current)){
                // make the other threads know that the goal is found 
                foundgoal.set(true);
                // move to the goal
                maze.move(player,current);
                step++;
                //return the path from the start to the goal
                return pathFromTo(start, current);
            }
            // If the goal is not found, continue with checking if the current node has been visited
            else if(visited.add(current)){
                
                maze.move(player,current);
                step++;
                
                // Create a list to keep track of unvisited neighbors
                List<Integer> neighborsList = new LinkedList<Integer>();
                for(int nb: maze.neighbors(current)){
                    if(!visited.contains(nb)){
                        neighborsList.add(nb);
                    }
                }

                //
                for(int nb : neighborsList){ 
                   // if(!visited.contains(nb)){
                        predecessor.put(nb,current);
                        frontier.push(nb);
                        System.out.println("player: " + player + " number of neighbors: " + maze.neighbors(current).size() + "steps:" + step);

                        if((step >= forkAfter) && (neighborsList.size() > 1)){
                            step = 0;
                            ForkJoinSolver fjs = new ForkJoinSolver(maze,forkAfter,nb,visited);
                            solvers.add(fjs);
                            fjs.fork();
                            System.out.println("fork!!!");
                            
                        }
                  //  }
                }
            }
            }
             return joinPaths();
         }
         // all nodes explored, no goal found
       
    

    private List<Integer> joinPaths(){
        for(ForkJoinSolver fs: solvers){
            List<Integer> result = fs.join();
            if(result != null) {
                List<Integer> pathToGoal = pathFromTo(start, predecessor.get(fs.start));
                pathToGoal.addAll(result);
                return pathToGoal;

            }
        }
        return null;
    }
}
