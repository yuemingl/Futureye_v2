package edu.uta.futureye.test;

import static edu.uta.futureye.function.FMath.C0;
import static edu.uta.futureye.function.FMath.r;
import static edu.uta.futureye.function.FMath.s;
import static edu.uta.futureye.function.FMath.x;
import static edu.uta.futureye.function.FMath.y;

import java.util.HashMap;
import java.util.Map;

import edu.uta.futureye.algebra.SparseMatrixRowMajor;
import edu.uta.futureye.algebra.SparseVectorHashMap;
import edu.uta.futureye.algebra.intf.SparseMatrix;
import edu.uta.futureye.algebra.intf.SparseVector;
import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.algebra.solver.external.SolverJBLAS;
import edu.uta.futureye.bytecode.CompiledFunc;
import edu.uta.futureye.core.DOF;
import edu.uta.futureye.core.DOFOrder;
import edu.uta.futureye.core.Element;
import edu.uta.futureye.core.Mesh;
import edu.uta.futureye.core.NodeType;
import edu.uta.futureye.function.basic.FX;
import edu.uta.futureye.function.intf.MathFunc;
import edu.uta.futureye.function.operator.FOIntegrate;
import edu.uta.futureye.io.MeshReader;
import edu.uta.futureye.io.MeshWriter;
import edu.uta.futureye.lib.element.FELinearTriangle;
import edu.uta.futureye.util.Utils;
import edu.uta.futureye.util.container.DOFList;
import edu.uta.futureye.util.container.ElementList;


/**
 * This file express LHS explicitly by symbolic expression
 * <blockquote><pre>
 * Problem:
 *   -\Delta{u} = f
 *   u(x,y)=0, (x,y) \in \partial{\Omega}
 * where
 *   \Omega = [-3,3]*[-3,3]
 *   f = -2*(x^2+y^2)+36
 * Solution:
 *   u = (x^2-9)*(y^2-9)
 * </blockquote></pre>
 * 
 * @author liuyueming
 */
public class LaplaceTestJIT {
	public Mesh mesh;
	public Vector u;
	
	public void run() {
        //1.Read in a triangle mesh from an input file with
        //  format ASCII UCD generated by Gridgen
        MeshReader reader = new MeshReader("triangle.grd");
        Mesh mesh = reader.read2DMesh();
        //Compute geometry relationship between nodes and elements
        mesh.computeNodeBelongsToElements();

        //2.Mark border types
        HashMap<NodeType, MathFunc> mapNTF =
                new HashMap<NodeType, MathFunc>();
        mapNTF.put(NodeType.Dirichlet, null);
        mesh.markBorderNode(mapNTF);

        //3.Use element library to assign degrees of
        //  freedom (DOF) to element
        ElementList eList = mesh.getElementList();
        FELinearTriangle feLT = new FELinearTriangle();
        for(int i=1;i<=eList.size();i++)
            feLT.assignTo(eList.at(i));

		//Construct a function with the coordinate of points in an element as parameters
		String[] argsOrder = new String[]{"x1","x2","x3","y1","y2","y3","r","s","t"};
		FX x1 = new FX("x1");
		FX x2 = new FX("x2");
		FX x3 = new FX("x3");
		FX y1 = new FX("y1");
		FX y2 = new FX("y2");
		FX y3 = new FX("y3");
		MathFunc fx = x1*r + x2*s + x3*(1-r-s);
		MathFunc fy = y1*r + y2*s + y3*(1-r-s);
		Map<String, MathFunc> map = new HashMap<String, MathFunc>();
		map.put("x", fx);
		map.put("y", fy);
		
		//             (r[0] r[1])   (x_r, x_s)
		// 2D JacMat = (r[2] r[3]) = (y_r, y_s)
		//jac changes with element, define the expression for jac with linear element
		MathFunc jac = fx.diff("r")*fy.diff("s") - fy.diff("r")*fx.diff("s");

        //Right hand side(RHS): f = -2*(x^2+y^2)+36
        MathFunc f = -2*(x*x+y*y)+36;
		MathFunc ff = f.compose(map);
		
		//4.Weak form
		Element ee = eList.at(1); //a template element
		DOFList DOFs = ee.getAllDOFList(DOFOrder.NEFV);
		int nDOFs = DOFs.size();
		
		//形函数计算需要和单元关联
		for(int i=1;i<=nDOFs;i++) {
			DOFs.at(i).getSSF().assignElement(ee);
		}
		MathFunc[][] lhs = new MathFunc[nDOFs][nDOFs];
		MathFunc[] rhs = new MathFunc[nDOFs];
		for(int j=0; j<nDOFs; j++) {
			DOF dofI = DOFs.at(j+1);
			MathFunc v = dofI.getSSF();
//			for(int i=0; i<nDOFs; i++) {
//				DOF dofJ = DOFs.at(i+1);
//				MathFunc u = dofJ.getSSF();
//				//lhs[j][i] = (u.diff("x")*v.diff("x")+u.diff("y")*v.diff("y"))*jac;
//				//lhs[j][i] = (grad(u,"x","y").dot(grad(v,"x","y")))*jac;
//				lhs[j][i].setName("LHS"+i+""+j);
//			}
			rhs[j] = v*ff*jac;
			rhs[j].setName("RHS"+j);
		}
		
//		r_x = (y2-y3)/jac;
//		r_y = (x3-x2)/jac;
//		s_x = (y3-y1)/jac;
//		s_y = (x1-x3)/jac;
//		double[][] lhs = { //SymJava Example6
//				{rx*rx + ry*ry, rx*sx + ry*sy, rx*tx+ry*ty},
//				{sx*rx + sy*ry, sx*sx + sy*sy, sx*tx+sy*ty},
//				{tx*rx + ty*ry, tx*sx + ty*sy, tx*tx+ty*ty},
//		};
		
//		lhs[0][0] = ((y2-y3)/jac*(y2-y3)/jac             + (x3-x2)/jac*(x3-x2)/jac)*jac; --simplify-->
		
		lhs[0][0] = ((y2-y3)*(y2-y3)             + (x3-x2)*(x3-x2))/jac;
		lhs[0][1] = ((y2-y3)*(y3-y1)             + (x3-x2)*(x1-x3))/jac;
		lhs[0][2] = ((y2-y3)*(-(y2-y3)-(y3-y1))  + (x3-x2)*(-(x3-x2)-(x1-x3)))/jac;

		lhs[1][0] = ((y3-y1)*(y2-y3)             + (x1-x3)*(x3-x2))/jac;
		lhs[1][1] = ((y3-y1)*(y3-y1)             + (x1-x3)*(x1-x3))/jac;
		lhs[1][2] = ((y3-y1)*(-(y2-y3)-(y3-y1))  + (x1-x3)*(-(x3-x2)-(x1-x3)))/jac;
		
		lhs[2][0] = ((-(y2-y3)-(y3-y1))*(y2-y3)             + (-(x3-x2)-(x1-x3))*(x3-x2))/jac;
		lhs[2][1] = ((-(y2-y3)-(y3-y1))*(y3-y1)             + (-(x3-x2)-(x1-x3))*(x1-x3))/jac;
		lhs[2][2] = ((-(y2-y3)-(y3-y1))*(-(y2-y3)-(y3-y1))  + (-(x3-x2)-(x1-x3))*(-(x3-x2)-(x1-x3)))/jac;
		for(int j=0; j<nDOFs; j++) {
			for(int i=0; i<nDOFs; i++) {
				lhs[j][i].setName("LHS"+i+""+j);
			}
			rhs[j].setName("RHS"+j);
		}


		CompiledFunc[][] clhs = new CompiledFunc[nDOFs][nDOFs];
		CompiledFunc[] crhs = new CompiledFunc[nDOFs];
		for(int j=0; j<nDOFs; j++) {
			for(int i=0; i<nDOFs; i++) {
				clhs[j][i] = lhs[j][i].compile(argsOrder);
			}
			crhs[j] = rhs[j].compile(argsOrder);
		}
		
		//5.Assembly process
		double[][] A = new double[nDOFs][nDOFs];
		double[] b = new double[nDOFs];
		double[] params = new double[argsOrder.length];
		int dim = mesh.getNodeList().size();
		SparseMatrix stiff = new SparseMatrixRowMajor(dim,dim);;
		SparseVector load = new SparseVectorHashMap(dim);
		
		long start = System.currentTimeMillis();
		for(Element e : eList) {
			//e.adjustVerticeToCounterClockwise();

			DOFs = e.getAllDOFList(DOFOrder.NEFV);
			double[] coords = e.getNodeCoords();
			System.arraycopy(coords, 0, params, 0, coords.length);

//			for(int i=1;i<=nDOFs;i++) {
//				DOFs.at(i).getSSF().assignElement(e);
//				//DOFs.at(i).getSSF().assignElement(ee);
//			}
//			MathFunc[][] lhs = new MathFunc[nDOFs][nDOFs];
//			MathFunc[] rhs = new MathFunc[nDOFs];
//			for(int j=0; j<nDOFs; j++) {
//				DOF dofI = DOFs.at(j+1);
//				MathFunc v = dofI.getSSF();
//				for(int i=0; i<nDOFs; i++) {
//					DOF dofJ = DOFs.at(i+1);
//					MathFunc u = dofJ.getSSF();
//					//lhs[j][i] = (u.diff("x")*v.diff("x")+u.diff("y")*v.diff("y"))*jac;
//					lhs[j][i] = (grad(u,"x","y").dot(grad(v,"x","y")))*jac;
//					lhs[j][i].setName("LHS"+i+""+j);
//				}
//				rhs[j] = v*ff*jac;
//				rhs[j].setName("RHS"+j);
//			}
//			
//			CompiledFunc[][] clhs = new CompiledFunc[nDOFs][nDOFs];
//			CompiledFunc[] crhs = new CompiledFunc[nDOFs];
//			for(int j=0; j<nDOFs; j++) {
//				for(int i=0; i<nDOFs; i++) {
//					clhs[j][i] = lhs[j][i].compile(argsOrder);
//				}
//				crhs[j] = rhs[j].compile(argsOrder);
//			}
			
			
			for(int j=0; j<nDOFs; j++) {
				for(int i=0; i<nDOFs; i++) {
					A[j][i] = FOIntegrate.intOnTriangleRefElement(clhs[j][i], params, coords.length, 3);
				}
				b[j] = FOIntegrate.intOnTriangleRefElement(crhs[j], params, coords.length, 3);
			}
			
			for(int j=0;j<nDOFs;j++) {
				DOF dofI = DOFs.at(j+1);
				int nGlobalRow = dofI.getGlobalIndex();
				for(int i=0;i<nDOFs;i++) {
					DOF dofJ = DOFs.at(i+1);
					int nGlobalCol = dofJ.getGlobalIndex();
					stiff.add(nGlobalRow, nGlobalCol, A[j][i]);
				}
				//Local load vector
				load.add(nGlobalRow, b[j]);
			}
		}
		System.out.println("Aassembly time: "+(System.currentTimeMillis()-start)+"ms");

		//Boundary condition
		Utils.imposeDirichletCondition(stiff, load, mesh, C0);
		
/*        for(int i=1; i<=stiff.getRowDim(); i++) {
        	for(int j=1; j<=stiff.getRowDim(); j++) {
        		System.out.print(stiff.get(i,j)+" ");
        	}
        	System.out.println();
        }
        for(int i=1; i<=load.getDim(); i++) {
        	System.out.print(load.get(i)+" ");
        } */

        //6.Solve linear system
        SolverJBLAS solver = new SolverJBLAS();
        Vector u = solver.solveDGESV(stiff, load);
        System.out.println("u=");
        for(int i=1;i<=u.getDim();i++)
            System.out.println(String.format("%.3f ", u.get(i)));

        //7.Output results to an Techplot format file
        MeshWriter writer = new MeshWriter(mesh);
        writer.writeTechplot("./tutorial/Laplace2D.dat", u);

        this.mesh = mesh;
        this.u = u;
	}

    public static void main(String[] args) {
    	LaplaceTestJIT ex1 = new LaplaceTestJIT();
    	ex1.run();
    }
}
