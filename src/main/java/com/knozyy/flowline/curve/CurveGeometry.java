package com.knozyy.flowline.curve;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Our own cubic Hermite curves, shared by preview, server validation, picking and rendering. */
public final class CurveGeometry {
    public static final double RADIUS = 0.09, MAX_LENGTH = 16, REACH = 8;
    private CurveGeometry() {}

    public static final class Shape {
        private final Vec3 a,b,ta,tb;
        private List<Vec3> sampled;
        private AABB box;
        private double measured=-1;
        public Shape(Vec3 a,Vec3 b,Vec3 ta,Vec3 tb){this.a=a;this.b=b;this.ta=ta;this.tb=tb;}
        public Vec3 a(){return a;} public Vec3 b(){return b;} public Vec3 ta(){return ta;} public Vec3 tb(){return tb;}
        @Override public boolean equals(Object other){return other instanceof Shape s&&a.equals(s.a)&&b.equals(s.b)&&ta.equals(s.ta)&&tb.equals(s.tb);}
        @Override public int hashCode(){return java.util.Objects.hash(a,b,ta,tb);}
        public Vec3 at(double t) {
            double t2 = t * t, t3 = t2 * t;
            return a.scale(2*t3-3*t2+1).add(ta.scale(t3-2*t2+t))
                    .add(b.scale(-2*t3+3*t2)).add(tb.scale(t3-t2));
        }
        public List<Vec3> points() {
            if(sampled!=null)return sampled;
            int count = Math.max(8, Math.min(128, (int) Math.ceil((a.distanceTo(b) + ta.length() + tb.length()) * 4)));
            List<Vec3> result = new ArrayList<>(count + 1);
            for (int i = 0; i <= count; i++) result.add(at((double) i/count));
            sampled=List.copyOf(result);return sampled;
        }
        public double length() {
            if(measured>=0)return measured;
            List<Vec3> p = points();
            double length = 0;
            for (int i = 1; i < p.size(); i++) length += p.get(i-1).distanceTo(p.get(i));
            measured=length;return measured;
        }
        public AABB bounds() {
            if(box!=null)return box;
            AABB result = new AABB(a, b);
            for (Vec3 p : points()) result = result.minmax(new AABB(p, p));
            box=result.inflate(RADIUS);return box;
        }
    }

    public static Shape straight(Vec3 a, Vec3 b) {
        Vec3 tangent = b.subtract(a);
        return new Shape(a, b, tangent, tangent);
    }
    public static boolean finite(Vec3 p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
                && Math.abs(p.x) < 30_000_000 && Math.abs(p.z) < 30_000_000 && Math.abs(p.y) < 20_000;
    }
    public static Vec3 snap(Vec3 p, double grid) {
        return grid <= 0 ? p : new Vec3(Math.rint(p.x/grid)*grid, Math.rint(p.y/grid)*grid, Math.rint(p.z/grid)*grid);
    }
    public static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = ab.lengthSqr() < 1e-10 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab)/ab.lengthSqr()));
        return p.distanceTo(a.add(ab.scale(t)));
    }
}
