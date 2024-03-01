package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
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

    // @Override
    //   protected void initStructures()
    // {
    //     visited = new ConcurrentSkipListSet<>();
    //     predecessor = new HashMap<>();
    //     frontier = new Stack<>();
    // }
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
         int step = 0;
         int player = maze.newPlayer(start);
         
         // start with start node
         frontier.push(start);
         // as long as not all nodes have been processed
         while (!frontier.empty() && !foundgoal.get()) {
             // get the new node to process
             int current = frontier.pop();
             // if current node has a goal
             if (visited.add(current)||current==start) {
                if (maze.hasGoal(current)) {
                 // move player to goal
                 foundgoal.set(true);                 //Let every thread know that the goal has been found
                 maze.move(player, current);
                 step++;
                 
                 // search finished: reconstruct and return path
                 return pathFromTo(start, current);
                }
             
                 // move player to current node
                 maze.move(player, current);
                 step++;
                 boolean first = true;
                

                 // for every node nb adjacent to current
                 for (int nb: maze.neighbors(current)) {
                    
                     // if nb has not been already visited,
                     // nb can be reached from current (i.e., current is nb's predecessor)
                     if (!visited.contains(nb)){
                         predecessor.put(nb, current);
                         if(first || step < forkAfter){
                            // add nb to the nodes to be processed
                            frontier.push(nb);
                         }
                      else {
                        if(visited.add(nb)){
                            step = 0;
                            ForkJoinSolver forkedSolver = new ForkJoinSolver(maze, forkAfter,nb, visited);
                            solvers.add(forkedSolver);
                            forkedSolver.fork();
                        }
                      }

                    }  
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
