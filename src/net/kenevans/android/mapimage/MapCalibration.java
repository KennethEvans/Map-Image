package net.kenevans.android.mapimage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import Jama.Matrix;
import Jama.SingularValueDecomposition;
import android.content.Context;
import android.util.Log;

public class MapCalibration implements IConstants {
	private List<MapData> dataList = new ArrayList<MapData>();
	private MapTransform transform;
	private Context context;

	public MapCalibration(Context context) {
		this.context = context;
	}

	public boolean read(File file) throws NumberFormatException, IOException {
		MapData data = null;
		boolean ok = false;
		BufferedReader in = null;
		String[] tokens = null;
		int x, y;
		double lon, lat;
		in = new BufferedReader(new FileReader(file));
		String line;
		while ((line = in.readLine()) != null) {
			tokens = line.trim().split("\\s+");
			// Skip blank lines
			if (tokens.length == 0) {
				continue;
			}
			// Skip lines starting with #
			if (tokens[0].trim().startsWith("#")) {
				continue;
			}
			x = Integer.parseInt(tokens[0]);
			y = Integer.parseInt(tokens[1]);
			lon = Double.parseDouble(tokens[2]);
			lat = Double.parseDouble(tokens[3]);
			// DEBUG
			// System.out.println(String.format("x=%d y=%d lon=%.6f lat=%.6f",
			// x,
			// y, lon, lat));
			data = new MapData(x, y, lon, lat);
			dataList.add(data);
		}
		in.close();
		// Make the transform
		createTransform();
		return ok;
	}

	/**
	 * Calculates a, b, c, d, e, and f using singular value decomposition.
	 */
	protected void createTransform() {
		transform = null;
		if (dataList.size() < 3) {
			// SWTUtils.errMsg("Need at least three data points for calibration.");
			Utils.errMsg(context,
					"Need at least three data points for calibration.");
			return;
		}

		// Define the matrices
		int nPoints2 = 2 * dataList.size();
		Matrix aa = new Matrix(nPoints2, 6);
		Matrix bb = new Matrix(nPoints2, 1);
		MapData data = null;
		int row;
		for (int i = 0; i < dataList.size(); i++) {
			data = dataList.get(i);
			row = 2 * i;
			aa.set(row, 0, data.getX());
			aa.set(row, 1, data.getY());
			aa.set(row, 4, 1);
			bb.set(row, 0, data.getLon());
			row++;
			aa.set(row, 2, data.getX());
			aa.set(row, 3, data.getY());
			aa.set(row, 5, 1);
			bb.set(row, 0, data.getLat());
		}

		// Get the singular values
		try {
			SingularValueDecomposition svd = new SingularValueDecomposition(aa);
			Matrix u = svd.getU();
			Matrix v = svd.getV();
			Matrix wi = svd.getS();
			for (int i = 0; i < 6; i++) {
				wi.set(i, i, 1. / wi.get(i, i));
			}
			Matrix aainv = v.times(wi).times(u.transpose());
			Matrix xx = aainv.times(bb);

			// // DEBUG
			// System.out.println("u = ");
			// printMatrix(u);
			// System.out.println("s = ");
			// printMatrix(svd.getS());
			// System.out.println("v = ");
			// printMatrix(v);
			// System.out.println("aa = ");
			// printMatrix(aa);
			// Matrix aa1 = u.times(wi).times(v.transpose());
			// System.out.println("aa1 = ");
			// printMatrix(aa1);
			// System.out.println("wi = ");
			// printMatrix(wi);
			// System.out.println("aainv = ");
			// printMatrix(aainv);
			// System.out.println("xx = ");
			// printMatrix(xx);
			// Matrix test = aa.times(aainv);
			// System.out.println("aa.aainv = ");
			// printMatrix(test);

			double a = xx.get(0, 0);
			double b = xx.get(1, 0);
			double c = xx.get(2, 0);
			double d = xx.get(3, 0);
			double e = xx.get(4, 0);
			double f = xx.get(5, 0);
			transform = new MapTransform(a, b, c, d, e, f);
			// DEBUG
			System.out.println(String.format(
					"  a=%.3g b=%.3g c=%.3g d=%.3g e=%.3g f= %.3g",
					transform.getA(), transform.getB(), transform.getC(),
					transform.getD(), transform.getE(), transform.getF()));
		} catch (Exception ex) {
			// SWTUtils.excMsg("Failed to create calibration transform", ex);
			Utils.excMsg(context, "Failed to create calibration transform", ex);
			transform = null;
		}
	}

	/**
	 * Transforms the pixel coordinates (x,y) to (longitude, latitude).
	 * 
	 * @param x
	 * @param y
	 * @return {longitude, latitude}
	 */
	public double[] transform(int x, int y) {
		if (transform == null) {
			return null;
		}
		double[] val = new double[2];
		val[0] = transform.getA() * x + transform.getB() * y + transform.getE();
		val[1] = transform.getC() * x + transform.getD() * y + transform.getF();
		// DEBUG
		// System.out.println(String.format("x=%d y=%d lon=%.6f lat=%.6f", x, y,
		// val[0], val[1]));
		return val;
	}

	/**
	 * Calculates the pixel coordinates (x,y) corresponding to the given
	 * longitude and latitude.
	 * 
	 * @param lon
	 * @param lat
	 * @return {x, y}
	 */
	public int[] inverse(double lon, double lat) {
		if (transform == null) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ".inverse: transform is null.");
			return null;
		}
		int[] val = new int[2];
		double det = transform.getA() * transform.getD() - transform.getB()
				* transform.getC();
		if (det == 0) {
			// Log.d(TAG, this.getClass().getSimpleName()
			// + ".inverse: determinant is zero.");
			// Log.d(TAG, String.format(
			// "  a=%.3g b=%.3g c=%.3g d=%.3g e=%.3g f= %.3g",
			// transform.getA(), transform.getB(), transform.getC(),
			// transform.getD(), transform.getE(), transform.getF()));
			return null;
		}
		double v1, v2;
		v1 = (transform.getD() * (lon - transform.getE()) - transform.getB()
				* (lat - transform.getF()));
		v2 = (transform.getA() * (lat - transform.getF()) - transform.getC()
				* (lon - transform.getE()));
		v1 /= det;
		v2 /= det;
		val[0] = (int) (v1 + .5);
		val[1] = (int) (v2 + .5);
		// Log.d(TAG, this.getClass().getSimpleName() + ".inverse:");
		// Log.d(TAG, String.format(
		// "  a=%.3g b=%.3g c=%.3g d=%.3g e=%.3g f= %.3g",
		// transform.getA(), transform.getB(), transform.getC(),
		// transform.getD(), transform.getE(), transform.getF()));
		// Log.d(TAG, String.format("  v1=%.3f v2=%.3f", v1, v2));
		return val;
	}

	/**
	 * @return The value of transform.
	 */
	public MapTransform getTransform() {
		return transform;
	}

	public List<MapData> getDataList() {
		return dataList;
	}

	public class MapTransform {
		private double a;
		private double b;
		private double c;
		private double d;
		private double e;
		private double f;

		public MapTransform(double a, double b, double c, double d, double e,
				double f) {
			this.a = a;
			this.b = b;
			this.c = c;
			this.d = d;
			this.e = e;
			this.f = f;
		}

		public double getA() {
			return a;
		}

		public void setA(double a) {
			this.a = a;
		}

		public double getB() {
			return b;
		}

		public void setB(double b) {
			this.b = b;
		}

		public double getC() {
			return c;
		}

		public void setC(double c) {
			this.c = c;
		}

		public double getD() {
			return d;
		}

		public void setD(double d) {
			this.d = d;
		}

		public double getE() {
			return e;
		}

		public void setE(double e) {
			this.e = e;
		}

		public double getF() {
			return f;
		}

		public void setF(double f) {
			this.f = f;
		}

	}

	public class MapData {
		private int x;
		private int y;
		private double lon;
		private double lat;

		public MapData(int x, int y, double lon, double lat) {
			this.x = x;
			this.y = y;
			this.lon = lon;
			this.lat = lat;
		}

		public int getX() {
			return x;
		}

		public void setX(int x) {
			this.x = x;
		}

		public int getY() {
			return y;
		}

		public void setY(int y) {
			this.y = y;
		}

		public double getLon() {
			return lon;
		}

		public void setLon(double lon) {
			this.lon = lon;
		}

		public double getLat() {
			return lat;
		}

		public void setLat(double lat) {
			this.lat = lat;
		}

	}

}
