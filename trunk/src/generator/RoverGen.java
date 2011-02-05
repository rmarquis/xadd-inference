
// Package definition
package generator;

// Packages to import
import java.io.*;
import java.math.*;
import java.text.*;
import java.util.*;


/**
 * Generates rover problems 
 * 
 * @version 1.0
 * @author Karina
 * @language Java (JDK 1.3)
 **/
public class RoverGen {
	private int maxX=3;
	private int maxY=4;
	private String minTime="0";
	private String maxTime="57600";
	private String minEnergy="0";
	private String maxEnergy="20";
	private int secondPerMeter=60;
	private double energyPerMeter=0.2;
		
	public final static String  PATH_FILES="/home/karina/XADD/xadd-inference/src/cmdp/ex/";
	
/** Constants **/
	public static  String PROB_TURN   = "0.7";
	public static  String PROB_NOT_ARRIVE = "0.75";
	
	/** For printing **/
	public static DecimalFormat _df = new DecimalFormat("#.###");

	/** Generator **/
	public  void GenRoverFile(int size) {
		if (size < 2) {
			System.out.println("RoverGen: Size must be at least 2.");
			System.exit(1);
		}
		
		//compute distance
		ArrayList X= new ArrayList();
		ArrayList Y= new ArrayList();
		generatePairXY(X,Y,size);
		
		
		
		String filename =PATH_FILES+"rover" + size + ".cmdp";
		PrintWriter os = null;
		try {
			// Open the output file
			os = new PrintWriter(new FileOutputStream(filename));

			// Get all ids and print them to the file
			ArrayList<String> var_orderContinuous = new ArrayList<String>();
			var_orderContinuous.add("time");
			var_orderContinuous.add("energy");
			
			ArrayList<String> var_orderBinary = new ArrayList<String>();
			TreeSet<String> at_ids = new TreeSet<String>();
			for (int i = size ; i >= 1 ; i--) {
				at_ids.add("p" + i);
				var_orderBinary.add("p" + i);
			}
			TreeSet<String> taken_ids = new TreeSet<String>();
			for (int i = size ; i >= 1 ; i--) {
				taken_ids.add("takenp" + i);
				var_orderBinary.add("takenp" + i);
			}
			
			TreeSet<String> ids = new TreeSet<String>(var_orderBinary);

			os.print("cvariables (");
			for (String var : var_orderContinuous) {
				os.print(var + " ");
			}
			os.println(")");
			os.println("min-values ("+minTime+" "+minEnergy+")");
			os.println("max-values ("+maxTime+" "+maxEnergy+")");
			
			
			os.print("bvariables (");
			for (String var : var_orderBinary) {
				os.print(var + " ");
			}
			os.println(")");
			os.println("ivariables()");
			

			// Now, generate all actions
			//Actions move
			Iterator it2,it3,it4,it5;
			String idVar2, idVar3,idVar4,idVar5;
			it2 = at_ids.iterator();
			
			while (it2.hasNext()) {
				idVar2 = (String) it2.next();
				it3 = at_ids.iterator();
				while (it3.hasNext()) {
					idVar3 = (String) it3.next();
					if (idVar2.equals(idVar3)==false) {
						os.println("action move" + idVar2+idVar3);
						System.out.println("\nAction: move" + idVar2+idVar3);
						//print p1 ...pk
						it4 = at_ids.iterator();
						while (it4.hasNext()) {
							idVar4 = (String) it4.next();
							//pi does not change
							if (idVar4.equals(idVar2)==false && idVar4.equals(idVar3)==false) {
								os.println(idVar4+"' ( ["+idVar4+"] )");
							}
							else{
								
								if (idVar4.equals(idVar2)==true) {
									os.println(idVar4+"' ([energy>0.6 &" +idVar4+ "== 1]");
									os.println("([0])");
									os.println( "(["+idVar4+"])" );
									os.println( ")" );
								}
								else{
									os.println(idVar4+"' ([energy>0.6 &" +idVar2+ "== 1]");
									os.println("([1])");
									os.println( "(["+idVar4+"])" );
									os.println( ")" );
								}
							}
						
						}
						//print takenp1...takenpk
						it5 = taken_ids.iterator();
						while (it5.hasNext()) {
							idVar5 = (String) it5.next();
							os.println(idVar5+"' ( ["+idVar5+"] )");
                 
						}
						//print time and energy
						os.println("time' ([energy>0.6 &" +idVar2+ "== 1]");
						double distancepipj=computeDistanceXY(idVar2,idVar3,X,Y,size);
						double totaltime=distancepipj*secondPerMeter;
						os.println("([time + "+totaltime+"])");
						os.println("([time]) ");
						os.println( ")" );
						os.println("energy' ([energy>0.6 &" +idVar2+ "== 1]");
						double totalenergy=distancepipj*energyPerMeter; 
						os.println("([energy - "+ totalenergy+"])");
						os.println("([energy])");
						os.println( ")" );
						os.println("reward ([0])");
						os.println("endaction");						
					}
				
				}
			}
			//Actions takepicture	
			Iterator it6,it7,it8;
			String idVar6, idVar7,idVar8;
			it6 = at_ids.iterator();
			while (it6.hasNext()) {
				idVar6 = (String) it6.next();
				os.println("action takepicture" + idVar6);
				it7 = at_ids.iterator();
				//print p1 ...pk
				while (it7.hasNext()) {
					idVar7 = (String) it7.next();
					os.println(idVar7+"' ( ["+idVar7+"] )");
				}
				//print takenp1 ...takenpk
				it8 = taken_ids.iterator();
				while (it8.hasNext()) {
					idVar8 = (String) it8.next();
					//pi does not change
					if (idVar6.equals(idVar8.substring(5))==false) {
						os.println(idVar8+"' ( ["+idVar8+"] )");
					}
					else{
							os.println(idVar8+"'([energy>3 & "+idVar6 +"== 1 & time > 36000 & time < 50400 & "+idVar8+" == 0] ");
							os.println("([1])");
							os.println( "(["+idVar8+"])" );
							os.println( ")" );
					}
				}
				//TODO:
				//print time and energy
				os.println("time' ([energy>3 &" +idVar6+ "== 1& time > 36000 & time < 50400 & taken"+ idVar6 +"== 0]");
				os.println("([time + 600])");
				os.println("([time]) ");
				os.println( ")" );
				os.println("energy' ([energy>3 &" +idVar6+ "== 1& time > 36000 & time < 50400 & taken"+ idVar6 +"== 0]");
				os.println("([energy - 2])");
				os.println("([energy])");
				os.println( ")" );
				os.println("reward ([energy>3 &" +idVar6+ "== 1& time > 36000 & time < 50400 & taken"+ idVar6 +"== 0]");
				os.println("([100])");
				os.println("([0])");
				os.println( ")" );
				os.println("endaction");						
			}
			
					

			// Generate discount and iterations
			os.println("\n\ndiscount 1.0000000");
			os.println("iterations 10");

			// Close file
			os.close();

		} catch (IOException ioe) {
			System.out.println(ioe);
			System.exit(1);
		}
	}
	


	private double computeDistanceXY(String idVar2, String idVar3, ArrayList X,ArrayList Y, int size) {
		Integer pi=new Integer(idVar2.substring(1))-1;
		Integer pj=new Integer(idVar3.substring(1))-1;
		Integer xi=(Integer) X.get(pi);
		Integer yi=(Integer) Y.get(pi);
		Integer xj=(Integer) X.get(pj);
		Integer yj=(Integer) Y.get(pj);
		double distance= Math.sqrt(Double.parseDouble(Float.toString((xj-xi)*(xj-xi)+ (yj-yi)*(yj-yi))));
		return distance;
	}



	private void generatePairXY(ArrayList<Integer> X,ArrayList<Integer> Y, int size) {
		Random randomX=new Random(System.currentTimeMillis() );
		Random randomY=new Random(System.currentTimeMillis() );
		int ranx,rany;
		for (int i = size ; i >= 1 ; i--) {
		    ranx=randomX.nextInt(maxX);
	
		    boolean duplicate=false;
		    Iterator it = X.iterator();
			while (it.hasNext() & duplicate==false) {
				Integer x = (Integer) it.next();
				if (x.intValue() ==ranx) {
					duplicate=true;
					i++;
				}
			}
			if (duplicate==false){
				rany=randomY.nextInt(maxY);
				X.add(ranx);
				Y.add(rany);
			}
			
			
		}
		
	}

	/** Main **/
	public static void main(String[] args) {
		RoverGen tg=new RoverGen();
		tg.GenRoverFile(3);
		tg.GenRoverFile(4);
		/*tg.GenRoverFile(4);
		tg.GenRoverFile(5);
		tg.GenRoverFile(6);
		tg.GenRoverFile(7);
		tg.GenRoverFile(8);
		tg.GenRoverFile(9);
		tg.GenRoverFile(10);*/
 
		
	}
	

}
