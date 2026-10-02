package com.knozyy.flowline.curve;

import com.knozyy.flowline.network.CurveViewPayload;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Collection;
import java.util.function.Function;

public final class CurvePicking {
    public record Hit(int node, int edge, Vec3 point, double distance) {}
    private CurvePicking() {}
    public static Hit pick(Vec3 eye, Vec3 end, double limit, Collection<CurveViewPayload.Node> nodes,
                           Collection<CurveViewPayload.Edge> edges, Function<CurveViewPayload.Edge,CurveGeometry.Shape> shape) {
        Hit result=null;
        for(var n:nodes) {
            var hit=new AABB(n.point(),n.point()).inflate(0.15).clip(eye,end);
            if(hit.isPresent()){double d=hit.get().distanceTo(eye);if(d<=limit&&(result==null||d<result.distance()))result=new Hit(n.id(),0,n.point(),d);}
        }
        for(var edge:edges) {
            var curve=shape.apply(edge);if(curve==null||!curve.bounds().inflate(0.06).intersects(new AABB(eye,end)))continue;
            var p=curve.points();
            for(int i=1;i<p.size();i++){
                var hit=new AABB(p.get(i-1),p.get(i)).inflate(CurveGeometry.RADIUS+0.03).clip(eye,end);
                if(hit.isEmpty())continue;double d=hit.get().distanceTo(eye);
                if(d>limit || result!=null&&d>=result.distance())continue;
                Vec3 ab=p.get(i).subtract(p.get(i-1));double t=Math.max(0,Math.min(1,hit.get().subtract(p.get(i-1)).dot(ab)/ab.lengthSqr()));
                result=new Hit(0,edge.id(),p.get(i-1).add(ab.scale(t)),d);
            }
        }
        return result;
    }
}
