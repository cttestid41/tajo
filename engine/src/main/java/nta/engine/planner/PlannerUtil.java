package nta.engine.planner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import nta.catalog.Column;
import nta.engine.exec.eval.EvalNode.Type;
import nta.engine.exec.eval.EvalTreeUtil;
import nta.engine.exec.eval.FuncCallEval;
import nta.engine.parser.QueryBlock.Target;
import nta.engine.planner.logical.BinaryNode;
import nta.engine.planner.logical.CreateTableNode;
import nta.engine.planner.logical.ExprType;
import nta.engine.planner.logical.GroupbyNode;
import nta.engine.planner.logical.LogicalNode;
import nta.engine.planner.logical.LogicalNodeVisitor;
import nta.engine.planner.logical.ScanNode;
import nta.engine.planner.logical.SortNode;
import nta.engine.planner.logical.UnaryNode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Preconditions;

/**
 * @author Hyunsik Choi
 */
public class PlannerUtil {
  private static final Log LOG = LogFactory.getLog(PlannerUtil.class);
  
  public static LogicalNode insertNode(LogicalNode parent, LogicalNode newNode) {
    Preconditions.checkArgument(parent instanceof UnaryNode);
    Preconditions.checkArgument(newNode instanceof UnaryNode);
    
    UnaryNode p = (UnaryNode) parent;
    LogicalNode c = p.getSubNode();
    UnaryNode m = (UnaryNode) newNode;
    m.setInputSchema(c.getOutputSchema());
    m.setOutputSchema(c.getOutputSchema());
    m.setSubNode(c);
    p.setSubNode(m);
    
    return p;
  }
  
  public static LogicalNode insertOuterNode(LogicalNode parent, LogicalNode outer) {
    Preconditions.checkArgument(parent instanceof BinaryNode);
    Preconditions.checkArgument(outer instanceof UnaryNode);
    
    BinaryNode p = (BinaryNode) parent;
    LogicalNode c = p.getOuterNode();
    UnaryNode m = (UnaryNode) outer;
    m.setInputSchema(c.getOutputSchema());
    m.setOutputSchema(c.getOutputSchema());
    m.setSubNode(c);
    p.setOuter(m);
    return p;
  }
  
  public static LogicalNode insertInnerNode(LogicalNode parent, LogicalNode inner) {
    Preconditions.checkArgument(parent instanceof BinaryNode);
    Preconditions.checkArgument(inner instanceof UnaryNode);
    
    BinaryNode p = (BinaryNode) parent;
    LogicalNode c = p.getInnerNode();
    UnaryNode m = (UnaryNode) inner;
    m.setInputSchema(c.getOutputSchema());
    m.setOutputSchema(c.getOutputSchema());
    m.setSubNode(c);
    p.setInner(m);
    return p;
  }
  
  public static LogicalNode insertNode(LogicalNode parent, 
      LogicalNode left, LogicalNode right) {
    Preconditions.checkArgument(parent instanceof BinaryNode);
    Preconditions.checkArgument(left instanceof UnaryNode);
    Preconditions.checkArgument(right instanceof UnaryNode);
    
    BinaryNode p = (BinaryNode)parent;
    LogicalNode lc = p.getOuterNode();
    LogicalNode rc = p.getInnerNode();
    UnaryNode lm = (UnaryNode)left;
    UnaryNode rm = (UnaryNode)right;
    lm.setInputSchema(lc.getOutputSchema());
    lm.setOutputSchema(lc.getOutputSchema());
    lm.setSubNode(lc);
    rm.setInputSchema(rc.getOutputSchema());
    rm.setOutputSchema(rc.getOutputSchema());
    rm.setSubNode(rc);
    p.setOuter(lm);
    p.setInner(rm);
    return p;
  }
  
  public static LogicalNode transformGroupbyTo2P(GroupbyNode gp) {
    Preconditions.checkNotNull(gp);
        
    try {
      GroupbyNode child = (GroupbyNode) gp.clone();
      gp.setSubNode(child);
      gp.setInputSchema(child.getOutputSchema());
      gp.setOutputSchema(child.getOutputSchema());
    
      Target [] targets = gp.getTargetList();
      for (int i = 0; i < gp.getTargetList().length; i++) {
        if (targets[i].getEvalTree().getType() == Type.FUNCTION) {
          Column tobe = child.getOutputSchema().getColumn(i);        
          FuncCallEval eval = (FuncCallEval) targets[i].getEvalTree();
          Collection<Column> tobeChanged = 
              EvalTreeUtil.findDistinctRefColumns(eval);
          EvalTreeUtil.changeColumnRef(eval, tobeChanged.iterator().next(), 
              tobe);
        }
      }
      
    } catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    
    return gp;
  }
  
  public static LogicalNode transformSortTo2P(SortNode sort) {
    Preconditions.checkNotNull(sort);
    
    try {
      SortNode child = (SortNode) sort.clone();
      sort.setSubNode(child);
      sort.setInputSchema(child.getOutputSchema());
      sort.setOutputSchema(child.getOutputSchema());
    } catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return sort;
  }
  
  public static LogicalNode transformGroupbyTo2PWithStore(GroupbyNode gb, 
      String tableId) {
    GroupbyNode groupby = (GroupbyNode) transformGroupbyTo2P(gb);
    return insertStore(groupby, tableId);
  }
  
  public static LogicalNode transformSortTo2PWithStore(SortNode sort, 
      String tableId) {
    SortNode sort2p = (SortNode) transformSortTo2P(sort);
    return insertStore(sort2p, tableId);
  }
  
  private static LogicalNode insertStore(LogicalNode parent, 
      String tableId) {
    CreateTableNode store = new CreateTableNode(tableId);
    insertNode(parent, store);
    
    return parent;
  }
  
  /**
   * Find the top node of the given plan
   * 
   * @param plan
   * @param type to find
   * @return a found logical node
   */
  public static LogicalNode findTopNode(LogicalNode plan, ExprType type) {
    Preconditions.checkNotNull(plan);
    Preconditions.checkNotNull(type);
    
    LogicalNodeFinder finder = new LogicalNodeFinder(type);
    plan.accept(finder);
    
    if (finder.getFoundNodes().size() == 0) {
      return null;
    }
    return finder.getFoundNodes().get(0);
  }
  
  public static class LogicalNodeFinder implements LogicalNodeVisitor {
    private List<LogicalNode> list = new ArrayList<LogicalNode>();
    private ExprType tofind;

    public LogicalNodeFinder(ExprType type) {
      this.tofind = type;
    }

    @Override
    public void visit(LogicalNode node) {
      if (node.getType() == tofind) {
        list.add(node);
      }
    }

    public List<LogicalNode> getFoundNodes() {
      return list;
    }
  }
}
