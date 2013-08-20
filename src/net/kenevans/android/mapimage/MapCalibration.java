//Copyright (c) 2011 Kenneth Evans
//
//Permission is hereby granted, free of charge, to any person obtaining
//a copy of this software and associated documentation files (the
//"Software"), to deal in the Software without restriction, including
//without limitation the rights to use, copy, modify, merge, publish,
//distribute, sublicense, and/or sell copies of the Software, and to
//permit persons to whom the Software is furnished to do so, subject to
//the following conditions:
//
//The above copyright notice and this permission notice shall be included
//in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
//MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
//IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
//CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
//TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.kenevans.android.mapimage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MapCalibration {
	private List<MapData> dataList = new ArrayList<MapData>();
	private MapTransform transform;

	public boolean read(File file) throws NumberFormatException,
			IOException {
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
				in.close();
				continue;
			}
			// Must be 4 values
			if (tokens.length != 4) {
				in.close();
				return ok;
			}
			x = Integer.parseInt(tokens[0]);
			y = Integer.parseInt(tokens[1]);
			lon = Double.parseDouble(tokens[2]);
			lat = Double.parseDouble(tokens[3]);
			data = new MapData(x, y, lon, lat);
			dataList.add(data);
		}
		in.close();
		// Make the transform
		createTransform();
		return ok;
	}

	private void createTransform() {
		transform = null;
		int x1, x2, y1, y2;
		double lon1, lon2, lat1, lat2;
		if (dataList.size() < 2) {
			return;
		}
		try {
			x1 = dataList.get(0).getX();
			y1 = dataList.get(0).getY();
			lon1 = dataList.get(0).getLon();
			lat1 = dataList.get(0).getLat();
			x2 = dataList.get(1).getX();
			y2 = dataList.get(1).getY();
			lon2 = dataList.get(1).getLon();
			lat2 = dataList.get(1).getLat();
			double a = (lon1 - lon2) / (x1 - x2);
			double b = 0;
			double c = (lat1 - lat2) / (y1 - y2);
			double d = 0;
			double e = lon1 - a * x1;
			double f = lat1 - c * y1;
			transform = new MapTransform(a, b, c, d, e, f);
		} catch (Exception ex) {
			transform = null;
		}
	}

	public double[] transform(int x, int y) {
		if (transform == null) {
			return null;
		}
		double[] val = new double[2];
		val[0] = transform.getA() * x + transform.getB() * y + transform.getE();
		val[1] = transform.getC() * x + transform.getD() * y + transform.getF();
		return val;
	}

	public int[] inverse(double lon, double lat) {
		if (transform == null) {
			return null;
		}
		int[] val = new int[2];
		double det = transform.getA() * transform.getD() - transform.getB()
				* transform.getC();
		if (det == 0) {
			return null;
		}
		val[0] = (int) ((transform.getD() * (lon - transform.getE()) - transform
				.getB() * (lat - transform.getF()))
				/ det + .5);
		val[1] = (int) (-(transform.getC() * (lon - transform.getE()) + transform
				.getA() * (lat - transform.getF()))
				/ det + .5);
		return val;
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
