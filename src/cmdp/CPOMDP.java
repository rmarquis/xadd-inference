//////////////////////////////////////////////////////////////////////////
//
// File:     MDP.java
// Author:   Scott Sanner, University of Toronto (ssanner@cs.toronto.edu)
// Date:     9/1/2003
//
// Description:
//
//   An MDP inference package that uses both Tables, ADDs, AADDs as the
//   underlying computational mechanism.  (All via the logic.add.FBR
//   interface.)  See SPUDD (Hoey et al, UAI 1999) for more details on the
//   algorithm.
//
//////////////////////////////////////////////////////////////////////////

// Package definition
package cmdp;

// Packages to import
import graph.Graph;

import java.io.*;
import java.math.*;
import java.text.*;
import java.util.regex.*;
import java.util.*;


import util.IntTriple;
import xadd.XADD;
import xadd.XADD.ArithExpr;
import xadd.XADD.BoolDec;
import xadd.XADD.DoubleExpr;
import xadd.XADD.OperExpr;
import xadd.XADD.VarExpr;
import xadd.XADD.XADDINode;
import xadd.XADD.XADDNode;
import xadd.XADD.XADDTNode;

// DD & FBR interfaces

/**
 * Main CPOMDP inference class
 * 
 * @version 1.0
 * @author Zahra Zamani
 **/
public class CPOMDP {

	
	/* Constants */
	public final static boolean REDUCE_LP = true;
	
	public final static boolean DISPLAY_Q = false;
	public final static boolean DISPLAY_V = true;
	public final static boolean DISPLAY_MAX = false;
	public final static boolean DISPLAY_SUBST = false;
	public final static boolean PRINTFINALQ = false;
	public final static boolean PRINTSCREENEVAL = false;
	public final static boolean ALWAYS_FLUSH = false; // Always flush DD caches?
	public final static double FLUSH_PERCENT_MINIMUM = 0.3d; // Won't flush until < amt
	
	public static boolean PRINT3DFILE;
	public static String varX;
	public static String varY;
	
	public static double size3D = 10d; // Won't flush until < amt
	public final static ArrayList<String> ZERO  =  new ArrayList<String> (Arrays.asList("[0]"));  
	/* For printing */
	public static DecimalFormat _df = new DecimalFormat("#.###");
	public static String  NAME_FILE_3D="./src/cmdp/ex/data/POMDP3D";
	
	/* Static variables */
	public static long _lTime; // For timing purposes
	public static Runtime RUNTIME = Runtime.getRuntime();

	/* Local vars */
	public XADD _context = null;
	public ArrayList<String> _alCVars; 
	public ArrayList<String> _alBVars; 
	public ArrayList<String> _alIVars; 
	public ArrayList<String> _alOVars;
	public HashMap<Integer,Double>  _belief;
	
	public Integer _alphaDD; 
	public Integer _valueDD;						
	public Integer _maxDD;
	public Integer _prevDD;
	public BigDecimal _bdDiscount;   // Discount (gamma) for CMDP
	public Integer    _nIter; // Tolerance (gamma) for CMDP
	
	public HashMap<String,Action> _hmName2Action;
	public HashMap<Integer,Integer> _hmGammaXadd; // for each horizon, keep xadd_id  
	public ArrayList<Integer> _alConstraints;
	public HashMap<String,Integer> _hmStateObsMapping; // progressed observations kept here
	public HashMap<IntTriple,Integer> _hmRegrKey2Node;
	
	////////////////////////////////////////////////////////////////////////////
	// Constructors
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Constructor - filename
	 **/
	public CPOMDP(String filename) {
		this(HierarchicalParser.ParseFile(filename));
		NAME_FILE_3D=NAME_FILE_3D+filename.substring(12,filename.indexOf("."))+".dat";
	}

	/**
	 * Constructor - pre-parsed file
	 **/
	public CPOMDP(ArrayList input) {
		_context = new XADD();
		_prevDD = _maxDD = _alphaDD = null;
		_bdDiscount = new BigDecimal("" + (-1));
		_nIter = null;
		_hmName2Action = new HashMap<String,Action>();
		_hmRegrKey2Node = new HashMap<IntTriple,Integer>();
		_belief =new HashMap<Integer, Double>();
		_hmGammaXadd = new HashMap<Integer, Integer>();
		_hmStateObsMapping = new HashMap<String, Integer>();
		buildCPOMDP(input);
	}

	////////////////////////////////////////////////////////////////////////////
	// Generic CMDP Inference Methods
	////////////////////////////////////////////////////////////////////////////

	/**
	 * CMDP inference methods
	 **/
	public int solve(int max_iter) {

		// ////////////////////////////////////////////////////////////
		// Set value function equal to reward
		// ////////////////////////////////////////////////////////////
		_alphaDD = _context.buildCanonicalXADD(ZERO); // start with alpha_0 = 0
		_hmGammaXadd.put(0, _alphaDD); // for first horizon
		_belief.put(0, 0.5);//start with equal belief state  
		_belief.put(1, 0.5);
		
		// Other initialization -- iteration statistics
		int iter = 0;
		long[] time = new long[max_iter + 1];
		int[] num_nodes = new int[max_iter + 1]; 
		int[] num_branches = new int[max_iter + 1]; 
		double[] num_cases = new double[max_iter + 1]; 
		for (int i = 0; i <= max_iter; i++)
			num_cases[i] = 1;

		//////////////////////////////////////////////////////////////
		// Iterate until convergence (or max iterations)
		//////////////////////////////////////////////////////////////
		
		// Only use file number of iterations if not overridden by command line
		if (max_iter < 0)
			max_iter = _nIter;
		
		while (++iter <= max_iter) {

			ResetTimer();

			// Error decreasing?
			System.out.println("Iteration #" + iter + ", " + MemDisplay()
					+ " bytes, " + GetElapsedTime() + " ms");

			// Prime diagram
			_prevDD = _alphaDD;
			
			// TODO: Handle boolean variables
			//_valueDD = _context.remapGIDsInt(_valueDD, _hmPrimeRemap);

			//////////////////////////////////////////////////////////////
			// Iterate over each action
			//////////////////////////////////////////////////////////////
			_maxDD=null;
			
			for (Map.Entry<String,Action> me : _hmName2Action.entrySet()) {

				//////////////////////////////////////////////////////////////
				// Regress the current value function through each action
				//////////////////////////////////////////////////////////////
				int regr = regressGamma(_alphaDD, me.getValue());
				
				if (REDUCE_LP) {
					regr = _context.reduceLP(regr, _alCVars);
				}
				
				if (DISPLAY_Q) {
					Graph g = _context.getGraph(regr);
					g.addNode("_temp_");
					g.addNodeLabel("_temp_", "Q^" + iter + "(" + me.getKey() + ")");
					g.addNodeShape("_temp_", "square");
					g.addNodeStyle("_temp_", "filled");
					g.addNodeColor("_temp_", "lightblue");

					// g.genDotFile(type + "value.dot");
					g.launchViewer(1300, 770);
				}

				///////////////////////////////////////////////////////////////
				// Estimating number of cases
				double num_cases_in_regr = num_cases[iter - 1];
				for (Map.Entry<String,Integer> me2 : me.getValue()._hmVar2DD.entrySet()) {
					num_cases_in_regr *= _context.getBranchCount(me2.getValue()); // From regr
					if (_alBVars.contains(me2.getKey()) && num_cases_in_regr > 1)
						num_cases_in_regr /= 2; // Sum out a variable
				}
				num_cases_in_regr *= _context.getBranchCount( me.getValue()._reward);
				num_cases[iter] *= num_cases_in_regr; // From max

				//how to take union? just add it to the set?
				_hmGammaXadd.put(iter, regr);
				
			    flushCaches();

			}

			if (REDUCE_LP) {
//				Graph g1 = _context.getGraph(_maxDD);
//				g1.addNode("_temp_");
//				g1.addNodeLabel("_temp_", "Before [" + iter + "]");
//				g1.addNodeShape("_temp_", "square");
//				g1.addNodeStyle("_temp_", "filled");
//				g1.addNodeColor("_temp_", "lightblue");
//				g1.launchViewer(1300, 770);

				_maxDD = _context.reduceLP(_maxDD, _alCVars);
//				Graph g2 = _context.getGraph(_maxDD);
//				g2.addNode("_temp_");
//				g2.addNodeLabel("_temp_", "After [" + iter + "]");
//				g2.addNodeShape("_temp_", "square");
//				g2.addNodeStyle("_temp_", "filled");
//				g2.addNodeColor("_temp_", "lightblue");
//				g2.launchViewer(1300, 770);
			}
			
			// ////////////////////////////////////////////////////////////
			// Discount the max'ed value function backup and add in reward
			// ////////////////////////////////////////////////////////////
			
			//_valueDD = _context.apply(_rewardDD, 
			//			_context.scalarOp(_maxDD, _bdDiscount.doubleValue(), XADD.PROD), 
			//			XADD.SUM);
			_valueDD = _maxDD;
			time[iter] = GetElapsedTime();
			num_nodes[iter] = _context.getNodeCount(_valueDD);
			num_branches[iter] = _context.getBranchCount(_valueDD);
			System.out.println("Value function size @ end of iteration " + iter + 
					": " + num_nodes[iter] + " nodes = " + 
					num_branches[iter] + " cases" + " in " + time[iter] + " ms");
			
			//compute V(b) = max_{alpha from Gamma_h} (alpha * b)
			//belief is a probability over states, a vector of (|S|-1,1)
			//TODO
			for (int i=0;i<_belief.size();i++)
			{
				for (Map.Entry<Integer, Integer> horizon: _hmGammaXadd.entrySet())
				{
					if (horizon.getKey()==iter) //iterate over all alpha_functions in Gamma_h
					{
						int alpha_h = horizon.getValue();
						//_maxDD = ((_maxDD == null) ? regr :	_context.apply(_maxDD, regr, XADD.MAX));
					
						/*if (REDUCE_LP) {
							_maxDD = _context.reduceLP(_maxDD, _alCVars);
						}
						
						if(DISPLAY_MAX){
						    Graph g = _context.getGraph(_maxDD);
							g.addNode("_temp_");
							g.addNodeLabel("_temp_", "max-" + iter);
							g.addNodeShape("_temp_", "square");
							g.addNodeStyle("_temp_", "filled");
							g.addNodeColor("_temp_", "lightblue");
						    g.launchViewer(1300, 770);
						}
						*/
					}
				}
			}
			
			if (DISPLAY_V) {
				System.out.print("Displaying value function... ");
				Graph g = _context.getGraph(_valueDD);
				g.addNode("_temp_");
				g.addNodeLabel("_temp_", "V^" + iter);
				g.addNodeShape("_temp_", "square");
				g.addNodeStyle("_temp_", "filled");
				g.addNodeColor("_temp_", "lightblue");

				// g.genDotFile(type + "value.dot");
				g.launchViewer(1300, 770);
				System.out.println("done.");
			}
		}
        if(PRINT3DFILE){
        	System.out.print("Creating data file... ");
        	create3DDataFile(_valueDD,varX,varY); 
			System.out.println("done.");
        }
		
		// Flush caches and return number of iterations
        flushCaches();	
        
        System.out.println("\nValue iteration complete!");
        System.out.println(max_iter + " iterations took " + GetElapsedTime() + " ms");
        System.out.println("Canonical / non-canonical: " + XADD.OperExpr.ALREADY_CANONICAL + " / " + XADD.OperExpr.NON_CANONICAL);
        
        System.out.println("\nIteration Results summary");
        for (int i = 1; i <= max_iter; i++) {
        	String branch_count = num_branches[i] >= 0 
        		? "" + num_branches[i]
        		: " > " + XADD.MAX_BRANCH_COUNT; 
        	System.out.println("Iter " + i + ": nodes = " + num_nodes[i] + "\tbranches = " + branch_count + "\tcases = " + num_cases[i] + "\ttime = " + time[i] + " ms");
        }
        
		return iter;
	}

	public void flushCaches() {
		if (((double)RUNTIME.freeMemory() / 
		     (double)RUNTIME.totalMemory()) > FLUSH_PERCENT_MINIMUM) {
			System.out.println("No need to flush caches.");
		    return; // Still enough free mem to exceed minimum requirements
		}
		System.out.println("Before flush: " + _context._hmInt2Node.size() + " XADD nodes in use, " + "freeMemory: " + 
				_df.format(RUNTIME.freeMemory()/10e6d) + " MB = " + 
				_df.format(100d*RUNTIME.freeMemory()/(double)RUNTIME.totalMemory()) + "% available memory");

		_context.clearSpecialNodes();
		
		for (Action a : _hmName2Action.values()) {
		    _context.addSpecialNode(a._reward);
		    for (Integer xadd : a._hmVar2DD.values())
		    	_context.addSpecialNode(xadd);
		}
		if (_prevDD!=null){
			_context.addSpecialNode(_prevDD);
		}
		if (_maxDD!=null){
			_context.addSpecialNode(_maxDD);
		}
		if (_valueDD!=null){
			_context.addSpecialNode(_valueDD); 
		}
		_context.flushCaches();
		
		_hmRegrKey2Node.clear();
		
		System.out.println("After flush: " + _context._hmInt2Node.size() + " XADD nodes in use, " + "freeMemory: " + 
				_df.format(RUNTIME.freeMemory()/10e6d) + " MB = " + 
				_df.format(100d*RUNTIME.freeMemory()/(double)RUNTIME.totalMemory()) + "% available memory");
   }

	/**
	 * Regress a DD through an action
	 **/
	public int regressGamma(int vfun, Action a) {
      
		XADD.XADDNode n = _context.getNode(vfun);
		HashSet<String> vars = n.collectVars();
		ArrayList<String> cvar_names = new ArrayList<String>();
		HashMap<String,Integer> bvar_dds = new HashMap<String,Integer>();
		HashMap<String,ArithExpr> prime_subs = new HashMap<String,ArithExpr>();
		HashMap<String,ArithExpr> obs_subs = new HashMap<String,ArithExpr>();
		ArrayList<XADD.XADDNode> node_list = new ArrayList<XADD.XADDNode>();
		ArrayList<XADD.ArithExpr> subst = new ArrayList<XADD.ArithExpr>();
		int q =0;
		//compute g_a,o,j(s) 
		
		for (int j=0; j<_alOVars.size();j++)
		{
			//find the state equations according to observation, this means isolating state variables from the rest of the equation
			// note: in the input the observation variables are isolate
			//TODO: for now work only with linear equations, extending this! 
			int obsModel;
			int obs = a._hmObs2DD.get(_alOVars.get(j));
			obsModel = isolateObsVar(obs,_alOVars.get(j));
					
			
			//prime variables
			for (String var : vars) 
			{
				String var_prime = var + "'";
				prime_subs.put(var, new XADD.VarExpr(var_prime));
				Integer dd = a._hmVar2DD.get(var_prime);
				if (_alBVars.contains(var)) {
					bvar_dds.put(var_prime, dd); // Note: var is unprimed
				} else {
					cvar_names.add(var_prime);
					node_list.add(_context.getNode(dd)); //node_list has all XADDs from action a that are related with valueDD variables
					subst.add(null);
				}
			}

			System.out.println("\n" + a._sName);
			System.out.println("BVars: " + bvar_dds.keySet());
			System.out.println("CVars: " + cvar_names);
			System.out.println("Node:  " + node_list);
			System.out.println("Subst: " + subst);
			
			// Note: following is the regression procedure
			// (1) we substitute variable primes (boolean and continuous): v -> v'
			// (2) we regress the continuous variables c' one at a time 
			// (3) we multiply in and sum out the CPTs for each b'
			q = _context.substitute(vfun, prime_subs); 
		
			//main step: for each continuous or discrete state transition, perform: 
			//step1: regress the alpha's: 
			//step2: regress observation model p(o|s',a)
			//step3: take integral over continuous variables and sum over discrete variables
			
			
			// Do the *deterministic regression* for the continuous variables
			ArrayList<XADD.XADDNode> temp_node_list = new ArrayList<XADD.XADDNode>();
			ArrayList<ArithExpr> temp_subst = new ArrayList<ArithExpr>();
			ArrayList<String> temp_cvar_names = new ArrayList<String>();
			temp_node_list.add(null);
			temp_subst.add(null);
			temp_cvar_names.add(null);
			for (int i = 0; i < node_list.size(); i++) 
			{
			
				IntTriple regr_key = new IntTriple(q, node_list.get(i).hashCode(), cvar_names.get(i).hashCode());
				Integer cache_node = null;
				if ((cache_node = _hmRegrKey2Node.get(regr_key)) != null) 
					q = cache_node;
				else 
				{
			
					temp_node_list.set(0, node_list.get(i));
					temp_subst.set(0, subst.get(i)); // This is null, right?
					temp_cvar_names.set(0, cvar_names.get(i));
					System.out.println("*** Regressing " + temp_cvar_names + ", size before: " + _context.getNodeCount(q));
					q = regress(temp_node_list, temp_cvar_names, temp_subst, 0, q);//regress(_valueDD, a);
					System.out.println("*** - size before makeCanonical: " + _context.getNodeCount(q));
					q = _context.makeCanonical(q);
					System.out.println("*** - size after: " + _context.getNodeCount(q));
				
					_hmRegrKey2Node.put(regr_key, q);
				}	
			}
		
			// Do the *decision-theoretic regression* for the primed boolean variables
			// - multiply in CPT for v' 	// - marginalize out v'
			for (Map.Entry<String, Integer> e : bvar_dds.entrySet())
			{
				String var_prime = e.getKey();
				int var_id = _context.getVarIndex( _context.new BoolDec(var_prime), false);
				Integer dd_mult = e.getValue();
				System.out.println("- Summing out: " + var_prime + "/" + 
					           var_id + " in\n" + _context.getString(dd_mult));
				q = _context.apply(q, dd_mult, XADD.PROD);
			
				// Following is a safer way to marginalize in the event that two branches
				// of a boolean variable had equal probability and were collapsed.
				//q = _context.opOut(q, var_id, XADD.SUM);
				int restrict_high = _context.opOut(q, var_id, XADD.RESTRICT_HIGH);
				int restrict_low  = _context.opOut(q, var_id, XADD.RESTRICT_LOW);
				q = _context.apply(restrict_high, restrict_low, XADD.SUM);
			}
			
		//step2: regress the alpha's: 
			
		//step3: progress observation model p(o|s',a)	
			
		}
		
		
		
		// Multiply in discount and add reward
    	q = _context.apply(a._reward, 
				_context.scalarOp(q, _bdDiscount.doubleValue(), XADD.PROD), 
				XADD.SUM);
    	
		// Multiply in all constraints
		// Note: constraints are 1.0 in legal states and 0.0 in illegal states
		for (Integer constraint : _alConstraints) {
			q = _context.apply(q, constraint, XADD.PROD);
		}
		if(PRINTFINALQ){ 
			System.out.println("- Final Q(" + a._sName + "):\n" + _context.getString(q));
		}
    	//Graph gr = _context.getGraph(q);
		//gr.launchViewer(1300, 770);

    	return q;
	}
	
	private int isolateObsVar(int obsEquation,String obsVar) {
		//gets a node and returns the ID for isolated state equation based on observations
		XADD.XADDNode n1 = _context.getNode(obsEquation);
		//n1 does not have observation variable, only the replacement
		if (n1 instanceof XADDTNode)
		{
			ArithExpr tempExpr = ((XADDTNode) n1)._expr;
			//assume each observation belongs to one state only
			//TODO: what if this is not the case, no one-one correspondance between state<->Observation
			HashSet<String> cont_vars = new HashSet<String>();
			tempExpr.collectVars(cont_vars);
			for (String var : cont_vars) 
			{
				if (_alCVars.contains(var))
				{
					//build equation here, based on linear observation model
					if (tempExpr instanceof OperExpr)
					{
						if (((OperExpr) tempExpr)._type==_context.SUM)
						{
							//inside sum of products
							double operand1 =0d;
							double operand2 = 0d;
							for(int j=0;j<((OperExpr)tempExpr)._terms.size();j++)
							{
								if ((((OperExpr) tempExpr)._terms.get(j)) instanceof DoubleExpr)
									operand1 =  ((DoubleExpr)((OperExpr) tempExpr)._terms.get(j))._dConstVal;
								else if  ((((OperExpr) tempExpr)._terms.get(j)) instanceof OperExpr)//product of two terms
								{
									if (((OperExpr)(((OperExpr) tempExpr)._terms.get(j)))._type == _context.PROD)
										operand2 = ((DoubleExpr)(((OperExpr)(((OperExpr) tempExpr)._terms.get(j))))._terms.get(0))._dConstVal;
								}
							}
							operand1 = (operand1/ operand2)* (-1);
							operand2 = 1 / operand2;
							((OperExpr) tempExpr)._terms.set(0, ArithExpr.parse(operand2 + "*" + obsVar));
							((OperExpr) tempExpr)._terms.set(1, new DoubleExpr(operand1));
							_hmStateObsMapping.put(var, _context.getTermNode(tempExpr));
						}
						
					}
					else if (tempExpr instanceof VarExpr)
					{
						//xo = x, just put in map
						_hmStateObsMapping.put(var, _context.getTermNode(ArithExpr.parse(obsVar)));
						
					}
					else if (tempExpr instanceof DoubleExpr)
					{
						//not possible, not state dependent
					}
				}
			}
			return 0;
		}
		else 
		{
			XADDINode inode = (XADDINode) n1;

			int low = isolateObsVar(inode._low,obsVar);
			int high = isolateObsVar(inode._high,obsVar);
			return _context.getINode(inode._var, low, high);
		}

	}

	// entry point for descending on branches of next variable
	// or simply making substitutions if all variables to branch on are exhausted
	// TODO: Deal with non-canonical XADD result (call reduce)
	public int regress(ArrayList<XADD.XADDNode> node_list, 
		ArrayList<String> var_names, ArrayList<XADD.ArithExpr> subst, 
		int index, int vfun) {
		
		// Check if at terminal
		if (index >= node_list.size()) {
		    // Make substitution
			HashMap<String,ArithExpr> leaf_subs = new HashMap<String,ArithExpr>();
			for (int i = 0; i < var_names.size(); i++) 
				leaf_subs.put(var_names.get(i), subst.get(i));
			if(DISPLAY_SUBST){ 
							System.out.println("Substituting: " + leaf_subs + 
					"\ninto:" + _context.getString(vfun));
			}
			int sub_dd = _context.substitute(vfun, leaf_subs);
			//Graph gr = _context.getGraph(sub_dd);
			//gr.launchViewer(1300, 770);
			return sub_dd;
		}
		
		// Must be nonterminal so continue to recurse
		return regress2(node_list.get(index), node_list, var_names, subst, index, vfun);
	}
	
	// TODO: Deal with non-canonical XADD result (call reduce)
	// Recurses to leaves and returns substituted XADD... branches on
	// current diagram for current node until a leaf is reached indicating
	// a substitution... subst recorded then regress called with advance
	// to next variable transition
	public int regress2(XADD.XADDNode cur, ArrayList<XADD.XADDNode> node_list, 
			ArrayList<String> var_names, ArrayList<XADD.ArithExpr> subst, 
			int index, int vfun) {
		
		if (cur instanceof XADD.XADDINode) {
			// Branch each way and recombine in new result
			XADD.XADDINode inode = (XADD.XADDINode)cur;
			XADD.XADDNode low = _context.getNode(inode._low);
			int new_low = regress2(low, node_list, var_names, subst, index, vfun);
			XADD.XADDNode high = _context.getNode(inode._high);
			int new_high = regress2(high, node_list, var_names, subst, index, vfun);
			
			// TODO: Deal with non-canonical XADD (ordering could change due to subst)
			return _context.getINode(inode._var, new_low, new_high);
			
		} else { 
			// Terminal, so identify substition and continue to next index
			XADD.XADDTNode tnode = (XADD.XADDTNode)cur;
			subst.set(index, tnode._expr);
			// This substitution uncovered, advance to next index
			int ret_node = regress(node_list, var_names, subst, index + 1, vfun);
			subst.set(index, null);
			return ret_node;
		}
	}
	
	////////////////////////////////////////////////////////////////////////////
	// DD Cache Maintenance
	////////////////////////////////////////////////////////////////////////////

//	/**
//	 * Clear nodes on save list
//	 **/
//	public void clearSaveNodes() {
//		_alSaveNodes.clear();
//	}
//
//	/**
//	 * Add node to save list
//	 **/
//	public void saveNode(Object dd) {
//		_alSaveNodes.add(dd);
//	}
//
//	/**
//	 * Frees up memory... only do this if near limit?
//	 **/
//	public void flushCaches() {
//		if (!ALWAYS_FLUSH
//				&& ((double) RUNTIME.freeMemory() / (double) RUNTIME
//						.totalMemory()) > FLUSH_PERCENT_MINIMUM) {
//			return; // Still enough free mem to exceed minimum requirements
//		}
//
//		_context.clearSpecialNodes();
//		Iterator i = _hmName2Action.values().iterator();
//		while (i.hasNext()) {
//			Action a = (Action) i.next();
//			Iterator j = a._hsTransDDs.iterator();
//			while (j.hasNext()) {
//				_context.addSpecialNode(j.next());
//			}
//		}
//		_context.addSpecialNode(_rewardDD);
//		_context.addSpecialNode(_valueDD);
//		if (_maxDD != null)
//			_context.addSpecialNode(_maxDD);
//		if (_prevDD != null)
//			_context.addSpecialNode(_prevDD);
//
//		Iterator j = _alSaveNodes.iterator();
//		while (j.hasNext()) {
//			_context.addSpecialNode(j.next());
//		}
//		_context.flushCaches(false);
//	}

//	public double getRewardRange() {
//		return _context.getMaxValue(_rewardDD)
//				- _context.getMinValue(_rewardDD);
//	}

	////////////////////////////////////////////////////////////////////////////
	// CMDP Construction Methods
	////////////////////////////////////////////////////////////////////////////

	/**
	 * CMDP construction methods
	 **/
	public void buildCPOMDP(ArrayList input) {

		if (input == null) {
			System.out.println("Empty input file!");
			System.exit(1);
		}

		Iterator i = input.iterator();
		Object o;

		// Set up variables
		o = i.next();
		if (!(o instanceof String) || !((String) o).equalsIgnoreCase("cvariables")) {
			exit("Missing cvariable declarations: " + o);
		}
		o = i.next();
		_alCVars = (ArrayList<String>) ((ArrayList) o).clone();		
		o = i.next();
		if (!(o instanceof String) || !((String) o).equalsIgnoreCase("min-values")) {
			exit("Missing min-values declarations: " + o);
		}
		o = i.next();
		for (int index = 0; index < _alCVars.size(); index++) {
			String var = _alCVars.get(index);
			String val = ((ArrayList) o).get(index).toString();
			if (!val.trim().equalsIgnoreCase("x")) try {
				double min_val = Double.parseDouble(val);
				_context._hmMinVal.put(var, min_val);
			} catch (NumberFormatException nfe) {
				System.out.println("\nIllegal min-value: " + var + " = " + val + " @ index " + index);
				System.exit(1);
			}
		}
		o = i.next();
		if (!(o instanceof String) || !((String) o).equalsIgnoreCase("max-values")) {
			exit("Missing max-values declarations: " + o);
		}
		o = i.next();
		for (int index = 0; index < _alCVars.size(); index++) {
			String var = _alCVars.get(index);
			String val = ((ArrayList) o).get(index).toString();
			if (!val.trim().equalsIgnoreCase("x")) try {
				double max_val = Double.parseDouble(val);
				_context._hmMaxVal.put(var, max_val);
			} catch (NumberFormatException nfe) {
				System.out.println("\nIllegal max-value: " + var + " = " + val + " @ index " + index);
				System.exit(1);
			}
		}
		o = i.next();
		if (!(o instanceof String) || !((String) o).equalsIgnoreCase("bvariables")) {
			exit("Missing bvariable declarations: " + o);
		}
		// TODO: Add all boolean vars to XADD
		o = i.next();
		//System.out.println(o);
		_alBVars = (ArrayList<String>) ((ArrayList) o).clone();
		for (String var : _alBVars) {
			_context.getVarIndex(_context.new BoolDec(var), true);
			_context.getVarIndex(_context.new BoolDec(var + "'"), true);
		}
		// TODO: Add all intermediate boolean vars to XADD
		o = i.next();
		if (!(o instanceof String) || !((String) o).equalsIgnoreCase("ivariables")) {
			exit("Missing ivariable declarations: " + o);
		}
		o = i.next();
		_alIVars = (ArrayList<String>) ((ArrayList) o).clone();

		
		o = i.next();
		if (!(o instanceof String) || !((String) o).equalsIgnoreCase("ovariables")) {
			exit("Missing ovariable declarations: " + o);
		}
		// TODO: Add all observation vars to XADD
		o = i.next();
		//System.out.println(o);
		_alOVars = (ArrayList<String>) ((ArrayList) o).clone();
	
		
		
		// Set up actions
		while (true) {
			o = i.next();
			if (!(o instanceof String)
					|| !((String) o).equalsIgnoreCase("action")) {
				break;
			}

			// o == "action"
			String aname = (String) i.next();
			System.out.println("action: "+aname);
			HashMap<String,ArrayList> cpt_map = new HashMap<String,ArrayList>();
			HashMap<String,ArrayList> obs_map = new HashMap<String,ArrayList>();

			o = i.next();
			while (!((String) o).equalsIgnoreCase("observation")) {//endaction
				cpt_map.put((String) o, (ArrayList) i.next());
				o = i.next();
			}
			
			//Observation
			if (!(o instanceof String) || !((String) o).equalsIgnoreCase("observation")) {
				System.out.println("Missing observation declaration for action: "+aname +" "+ o);
				System.exit(1);
			}
			o=i.next();
			while (!((String) o).equalsIgnoreCase("reward")) {//endaction
				obs_map.put((String) o, (ArrayList) i.next());
				o = i.next();
			}

			// Set up reward
			if (!(o instanceof String) || !((String) o).equalsIgnoreCase("reward")) {
				System.out.println("Missing reward declaration for action: "+aname +" "+ o);
				System.exit(1);
			}
			o=i.next();
			ArrayList reward = (ArrayList) o;

			int reward_dd = _context.buildCanonicalXADD(reward);
			//Graph g = _context.getGraph(_rewardDD);
			//g.launchViewer(1300, 770);

			_hmName2Action.put(aname, new Action(this, aname, cpt_map, obs_map, reward_dd));
			o=i.next(); // endaction
			
		}

		// Check for constraints declaration (can be multiple)
		_alConstraints = new ArrayList<Integer>();
		while (true) {
			if (!(o instanceof String)
					|| !((String) o).equalsIgnoreCase("constraint")) {
				break;
			}

			o=i.next(); // get dd
			ArrayList next_constraint = (ArrayList) o;
			int next_constraint_dd = _context.buildCanonicalXADD(next_constraint);
			_alConstraints.add(next_constraint_dd);
			
			o = i.next(); // get endconstraint
			o = i.next(); // get constraint or discount
		}
		
		// Read discount and tolerance
		//o = i.next();
		if (!(o instanceof String)
				|| !((String) o).equalsIgnoreCase("discount")) {
			System.out.println("Missing discount declaration: " + o);
			System.exit(1);
		}
		_bdDiscount = ((BigDecimal) i.next());

		o = i.next();
		if (!(o instanceof String)
				|| !((String) o).equalsIgnoreCase("iterations")) {
			System.out.println("Missing iterations declaration: " + o);
			System.exit(1);
		}
		_nIter = new Integer((String)i.next());
	}

	public void exit(String msg) {
		System.out.println(msg);
		System.exit(1);
	}
	
	////////////////////////////////////////////////////////////////////////////
	// Miscellaneous
	////////////////////////////////////////////////////////////////////////////

	/*
	 * Set the value of the other continous variables with the maxValue, without boolean assigments 
	 */
	
	public void create3DDataFile(Integer XDD, String xVar, String yVar) {
		try {
     		
            BufferedWriter out = new BufferedWriter(new FileWriter(NAME_FILE_3D));
            
            HashMap<String,Boolean> bool_assign = new HashMap<String,Boolean>();
            	
            for (String var : _alBVars) {
       		   bool_assign.put(var, false);	
       		}
            
             Double minX= _context._hmMinVal.get(xVar);
             Double maxX= _context._hmMaxVal.get(xVar);
             Double incX= (maxX-minX)/(size3D-1);
             
             Double minY= _context._hmMinVal.get(yVar);
             Double maxY= _context._hmMaxVal.get(yVar);
             Double incY= (maxY-minY)/(size3D-1);
             
             
            ArrayList<Double> X = new ArrayList<Double>();
            ArrayList<Double> Y = new ArrayList<Double>();
            

             Double xval=minX;
             Double yval=minY;
             for(int i=0;i<size3D;i++){
            	 X.add(xval);
            	 Y.add(yval);
            	 xval=xval+incX;
            	 yval=yval+incY;
             }
             System.out.println(">> Evaluations");
             for(int i=0;i<size3D;i++){
                 out.append(X.get(i).toString()+" ");
                 out.append(Y.get(i).toString()+" ");
                 for(int j=0;j<size3D;j++){
                	 
             
  		     		HashMap<String,Double> cont_assign = new HashMap<String,Double>();
  		     		
  		     		for (Map.Entry<String,Double> me : _context._hmMinVal.entrySet()) {
  		     			cont_assign.put(me.getKey(),  me.getValue());
  		     		}
  		     			     		
              		cont_assign.put(xVar,  X.get(j));
              		cont_assign.put(yVar,  Y.get(i));
              		
              		Double z=_context.evaluate(XDD, bool_assign, cont_assign);
              		if (PRINTSCREENEVAL){
             		System.out.println("Eval: [" + bool_assign + "], [" + cont_assign + "]"
             						   + ": " + z);
              		}

             		out.append(z.toString()+" ");
                   /*
             		cont_assign.put(xVar,  200.0d/3.0d);
              		cont_assign.put(yVar,  100.0d/3.0d);
              		z=_context.evaluate(XDD, bool_assign, cont_assign);
             		System.out.println("Eval: [" + bool_assign + "], [" + cont_assign + "]"
             						   + ": " + z);		

             		out.append(z.toString()+" ");
              		*/
              		
             		
                 }
                 out.newLine();
             }
            //out.append(System.getProperty("line.separator"));
             out.close();
             
             
             
         } catch (IOException e) {
         	System.out.println("Problem with the creation 3D file");
         	System.exit(0);
         }
	}
	
	
	public String toString() {
		return toString(false, false);
	}

	public String toString(boolean display_reward, boolean display_value) {
		StringBuffer sb = new StringBuffer();
		sb.append("\nCMDP Definition:\n===============\n");
		sb.append("CVars:       " + _alCVars + "\n");
		sb.append("Min-values:  " + _context._hmMinVal + "\n");
		sb.append("Max-values:  " + _context._hmMaxVal + "\n");
		sb.append("BVars:       " + _alBVars + "\n");
		sb.append("IVars:       " + _alIVars + "\n");
		sb.append("OVars:       " + _alOVars + "\n");
		sb.append("Order:       " + _context._alOrder + "\n");
		sb.append("Discount:    " + _bdDiscount + "\n");
		sb.append("Iterations:  " + _nIter + "\n");
		sb.append("Constraints (" + _alConstraints.size() + "):\n");
		for (Integer cons : _alConstraints) {
			sb.append("- " + _context.getString(cons) + "\n");
		}
		sb.append("Actions (" + _hmName2Action.size() + "):\n");
		for (Action a : _hmName2Action.values()) {
			sb.append("\n==> " + a);
		}
		sb.append("\n");

		if (display_value) {
			Graph g = _context.getGraph(_valueDD);
			g.launchViewer(1300, 770);
		}

		return sb.toString();
	}

	// Reset elapsed time
	public void ResetTimer() {
		_lTime = System.currentTimeMillis(); 
	}

	// Get the elapsed time since resetting the timer
	public long GetElapsedTime() {
		return System.currentTimeMillis() - _lTime;
	}

	public static String MemDisplay() {
		long total = RUNTIME.totalMemory();
		long free = RUNTIME.freeMemory();
		return total - free + ":" + total;
	}

	////////////////////////////////////////////////////////////////////////////
	// Testing Interface
	////////////////////////////////////////////////////////////////////////////

	public static void usage() {
		System.out.println("\nMust enter: POMDP-filename");
		System.out.println("\nMust enter: number of iterations");
		System.out.println("\nMust enter: print 3D file?");
		
		System.exit(1);
	}
	
	/**
	 * Basic testing interface.
	 **/
	public static void main(String args[]) {
		if (args.length < 2 || args.length > 7) {
			usage();
		}

		// Parse problem filename
		String filename = args[0];

		// Parse iterations
		int iter = -1;
		try {
			iter = Integer.parseInt(args[1]);
		} catch (NumberFormatException nfe) {
			System.out.println("\nIllegal iteration value\n");
			System.exit(1);
		}
		catch(ArrayIndexOutOfBoundsException aiobe){
			System.out.println("\nIMissing argument\n");
			usage();
		}
		
		PRINT3DFILE = false;
		try {
			if (args.length >= 3)
				PRINT3DFILE = Boolean.parseBoolean(args[2]);
		} catch (NumberFormatException nfe) {
			System.out.println("\nIllegal print3DFile value: "+ args[2] + "\n");
			System.exit(1);
		}
		
		if (PRINT3DFILE & args.length==7){ 
			  //rover = Boolean.parseBoolean(args[3]);
			  varX = args[4];
			  varY = args[5];
			  size3D= Double.parseDouble(args[6]);
		}
		else{
			if (PRINT3DFILE){
				System.out.println("\nMust enter: is it a rover problem?");
				System.out.println("\nMust enter: X var");
				System.out.println("\nMust enter: Y var");
				System.out.println("\nMust enter: size 3D");
				System.exit(1);
			}
		}
			
		// Build a CMDP, display, solve
		CPOMDP mdp1 = new CPOMDP(filename);
		System.out.println(mdp1.toString(false, false));
		//System.exit(1);
		
		int  iter1 = mdp1.solve(iter);
		long time1 = mdp1.GetElapsedTime();
	}
}
