package edu.uta.futureye.application;

import edu.uta.futureye.algebra.intf.Matrix;
import edu.uta.futureye.algebra.intf.SparseMatrix;
import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.algebra.solver.Solver;
import edu.uta.futureye.algebra.solver.external.SolverJBLAS;
import edu.uta.futureye.core.Mesh;
import edu.uta.futureye.core.NodeType;
import edu.uta.futureye.function.AbstractMathFunc;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.basic.FX;
import edu.uta.futureye.function.intf.MathFunc;
import edu.uta.futureye.io.MeshReader;
import edu.uta.futureye.io.MeshReaderForTriangle;
import edu.uta.futureye.io.MeshWriter;
import edu.uta.futureye.lib.assembler.AssemblerScalar;
import edu.uta.futureye.lib.element.FELinearTriangle;
import edu.uta.futureye.lib.weakform.WeakFormLaplace2D;
import edu.uta.futureye.tutorial.Tools;
import edu.uta.futureye.util.container.ElementList;
import static edu.uta.futureye.function.FMath.*;

import java.util.HashMap;

public class RoadLandmine {
		public Mesh mesh;
		public Vector u;
		public void run() {
	        //1.Read in a triangle mesh from an input file with
	        //  format ASCII UCD generated by Gridgen
			MeshReaderForTriangle reader = new MeshReaderForTriangle("E:/triangle/road.1.node","E:/triangle/road.1.ele");
	        Mesh mesh = reader.read2DMesh();
	        //Compute geometry relationship of nodes and elements
	        mesh.deleteIsolatedNode();
	        mesh.computeNodeBelongsToElements();
	        
	        class MyFun extends AbstractMathFunc {
	        	int x0=233;
	        	int y0=158;
	        	double R = 9.0;
	        	double mult = 1.0;
	        	public MyFun() {
	        		super("x","y");
	        	}
				public double apply(Variable v) {
					double x = v.get("x");
					double y = v.get("y");
					double r = Math.sqrt((x-x0)*(x-x0)+(y-y0)*(y-y0));
					if(r < R)
						return 18*mult*Math.pow(1.04, R-r);
					return 0;
				}
				public void setMult(double m) { this.mult = m; }
	        };
	        MyFun fun = new MyFun();
	        double[] mult = {0.1, 0.5, 0.75, 1-1/8., 1-1/16., 1-1/32., 1-1/64.};
	        for(int i=0; i<mult.length; ++i) {
	        	fun.setMult(mult[i]);
	        	Tools.plotFunction(mesh, "RoadLandmine", "road_v"+i+".dat", fun);
	        }
	        
	        
	        

	        //2.Mark border types
	        HashMap<NodeType, MathFunc> mapNTF =
	                new HashMap<NodeType, MathFunc>();
	        mapNTF.put(NodeType.Dirichlet, null);
	        mesh.markBorderNode(mapNTF);
	        //mesh.writeNodesInfo("./iphone/nodeInfo.dat");

	        //3.Use element library to assign degrees of
	        //  freedom (DOF) to element
	        ElementList eList = mesh.getElementList();
	        FELinearTriangle feLT = new FELinearTriangle();
	        for(int i=1;i<=eList.size();i++)
	            feLT.assignTo(eList.at(i));

	        //4.Weak form
	        WeakFormLaplace2D weakForm = new WeakFormLaplace2D();
	        //Right hand side(RHS): f = (x^2+y^2)
	        //weakForm.setF(X.M(X).A(Y.M(Y)));
	        weakForm.setF(C1);

	        //5.Assembly process
	        AssemblerScalar assembler =
	                new AssemblerScalar(mesh, weakForm);
	        assembler.assemble();
	        SparseMatrix stiff = assembler.getStiffnessMatrix();
	        Vector load = assembler.getLoadVector();
	        //Boundary condition
	        assembler.imposeDirichletCondition(C0);

	        //6.Solve linear system
	        Solver solver = new Solver();
	        Vector u = solver.solveCG(stiff, load);
	        System.out.println("u=");
	        for(int i=1;i<=u.getDim();i++)
	            System.out.println(String.format("%.3f", u.get(i)));

	        //7.Output results to an Techplot format file
	        MeshWriter writer = new MeshWriter(mesh);
	        writer.writeTechplot("./RoadLandmine/road2.dat", u);

	        this.mesh = mesh;
	        this.u = u;
		}

		/**
		 * @param args
		 */
		public static void main(String[] args) {
			// TODO Auto-generated method stub
			RoadLandmine hand = new RoadLandmine();
			hand.run();
		}
}
