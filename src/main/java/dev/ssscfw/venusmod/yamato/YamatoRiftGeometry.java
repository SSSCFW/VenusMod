package dev.ssscfw.venusmod.yamato;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 描画と接触に共用する、内側に湾曲した四頂点のアステロイド曲線。 */
public final class YamatoRiftGeometry {
    public static final double HALF_WIDTH = 1.25;
    public static final double HALF_HEIGHT = 1.6;
    public static final double CENTER_HEIGHT = 1.6;
    public static final double HALF_DEPTH = 0.18;
    public static final int FADE_TICKS = 6;
    private YamatoRiftGeometry() {}

    public static double pointX(double radians) {
        double cos = Math.cos(radians);
        return HALF_WIDTH * cos * cos * cos;
    }

    public static double pointY(double radians) {
        double sin = Math.sin(radians);
        return HALF_HEIGHT * sin * sin * sin;
    }

    public static boolean inside(double horizontal, double vertical) {
        return Double.isFinite(horizontal) && Double.isFinite(vertical)
                && Math.pow(Math.abs(horizontal) / HALF_WIDTH, 2.0 / 3.0)
                + Math.pow(Math.abs(vertical) / HALF_HEIGHT, 2.0 / 3.0) <= 1.0 + 1.0E-9;
    }

    /** ポータル座標系で軸に平行な箱と曲線面の接触。曲線の空白の隅は除外する。 */
    public static boolean intersects(double minU, double maxU, double minV, double maxV,
                                     double minDepth, double maxDepth) {
        if (minDepth > HALF_DEPTH || maxDepth < -HALF_DEPTH) return false;
        double u = minU > 0 ? minU : maxU < 0 ? maxU : 0;
        double v = minV > 0 ? minV : maxV < 0 ? maxV : 0;
        return inside(u, v);
    }

    /** 旧呼び出しは上下角0度として扱う。 */
    public static boolean intersectsWorldBox(double minX, double minY, double minZ,
                                             double maxX, double maxY, double maxZ,
                                             double yawDegrees, double scale) {
        return intersectsWorldBox(minX,minY,minZ,maxX,maxY,maxZ,yawDegrees,0,scale);
    }

    /**
     * 立体AABBを面の深さで切り取ってからUV面へ射影する。傾斜後のUV/深さの相関を失わない。
     * 射影した凸多角形を各象限で考えると、|u|^(2/3)+|v|^(2/3)は凹関数なので最小値は頂点にある。
     * その頂点（元の凸包、軸との交点、内部の原点）だけで凹曲線との交差を正確に判定できる。
     */
    public static boolean intersectsWorldBox(double minX, double minY, double minZ,
                                             double maxX, double maxY, double maxZ,
                                             double yawDegrees, double pitchDegrees, double scale) {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || !Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees) || !Double.isFinite(scale) || scale <= 0
                || minX > maxX || minY > maxY || minZ > maxZ) return false;
        Frame frame=frame(yawDegrees,pitchDegrees);
        Local[] corners=new Local[8];
        List<PlanePoint> projected=new ArrayList<>(32);
        for(int i=0;i<8;i++) {
            Vector p=new Vector((i&1)==0?minX:maxX,(i&2)==0?minY:maxY,(i&4)==0?minZ:maxZ);
            Local local=new Local(frame.right.dot(p),frame.up.dot(p),frame.normal.dot(p));
            if(!Double.isFinite(local.u)||!Double.isFinite(local.v)||!Double.isFinite(local.depth))return false;
            corners[i]=local;
            // ワールド端の大きな座標から中心を引くときのdouble丸めを吸収する。
            if(Math.abs(local.depth)<=HALF_DEPTH+1.0E-8)projected.add(new PlanePoint(local.u/scale,local.v/scale));
        }
        for(int i=0;i<8;i++) for(int bit=1;bit<=4;bit<<=1) {
            int other=i^bit;if(other<i)continue;
            Local a=corners[i],b=corners[other];
            for(double depth:new double[]{-HALF_DEPTH,HALF_DEPTH}) {
                if((a.depth<depth&&b.depth>depth)||(a.depth>depth&&b.depth<depth)) {
                    double t=(depth-a.depth)/(b.depth-a.depth);
                    projected.add(new PlanePoint((a.u+t*(b.u-a.u))/scale,(a.v+t*(b.v-a.v))/scale));
                }
            }
        }
        if(projected.isEmpty())return false;
        List<PlanePoint> hull=convexHull(projected);
        for(PlanePoint point:hull)if(inside(point.u,point.v))return true;
        if(hull.size()==1)return false;
        boolean containsOrigin=hull.size()>2;
        for(int i=0;i<hull.size();i++) {
            PlanePoint a=hull.get(i),b=hull.get((i+1)%hull.size());
            // 固定epsilonは短い辺の外積まで0扱いしてしまう。境界は軸の交点検査で拾う。
            if(a.u*b.v-a.v*b.u < 0)containsOrigin=false;
            if((a.u<0&&b.u>0)||(a.u>0&&b.u<0)) {
                double t=-a.u/(b.u-a.u);
                if(inside(0,a.v+t*(b.v-a.v)))return true;
            }
            if((a.v<0&&b.v>0)||(a.v>0&&b.v<0)) {
                double t=-a.v/(b.v-a.v);
                if(inside(a.u+t*(b.u-a.u),0))return true;
            }
        }
        return containsOrigin;
    }

    /** Minecraftの視線と同じ、Y(-yaw)の後にX(pitch)を掛ける右手系。 */
    public static Frame frame(double yawDegrees,double pitchDegrees) {
        double yaw=Math.toRadians(yawDegrees%360),pitch=Math.toRadians(pitchDegrees%360);
        double sy=Math.sin(yaw),cy=Math.cos(yaw),sp=Math.sin(pitch),cp=Math.cos(pitch);
        return new Frame(new Vector(cy,0,sy),new Vector(-sy*sp,cp,cy*sp),new Vector(-sy*cp,-sp,cy*cp));
    }

    /** 曲線の外接ひし形の4頂点と深さから、各ワールド軸の正確な半径を求める。 */
    public static Vector halfExtents(double yaw,double pitch,double size) {
        Frame f=frame(yaw,pitch);
        return new Vector(extent(f.right.x,f.up.x,f.normal.x,size),extent(f.right.y,f.up.y,f.normal.y,size),
                extent(f.right.z,f.up.z,f.normal.z,size));
    }

    private static double extent(double right,double up,double normal,double size) {
        return Math.max(Math.abs(right)*HALF_WIDTH*size,Math.abs(up)*HALF_HEIGHT*size)+Math.abs(normal)*HALF_DEPTH;
    }

    private static List<PlanePoint> convexHull(List<PlanePoint> points) {
        points.sort(Comparator.comparingDouble(PlanePoint::u).thenComparingDouble(PlanePoint::v));
        List<PlanePoint> unique=new ArrayList<>();
        for(PlanePoint p:points) if(unique.isEmpty()||!p.equals(unique.get(unique.size()-1)))unique.add(p);
        if(unique.size()<3)return unique;
        List<PlanePoint> hull=new ArrayList<>();
        for(PlanePoint p:unique) {
            while(hull.size()>=2&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);
            hull.add(p);
        }
        int lower=hull.size();
        for(int i=unique.size()-2;i>=0;i--) {
            PlanePoint p=unique.get(i);
            while(hull.size()>lower&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);
            hull.add(p);
        }
        hull.remove(hull.size()-1);
        return hull;
    }

    private static double cross(PlanePoint a,PlanePoint b,PlanePoint c) {
        return (b.u-a.u)*(c.v-a.v)-(b.v-a.v)*(c.u-a.u);
    }

    public record Vector(double x,double y,double z) {
        public double dot(Vector other){return x*other.x+y*other.y+z*other.z;}
    }
    public record Frame(Vector right,Vector up,Vector normal) {}
    private record Local(double u,double v,double depth) {}
    private record PlanePoint(double u,double v) {}

    /** closesAtは接触を無効にする時刻。その後6tickかけて自然に閉じる。 */
    public static double scale(double now, long openedAt, long closesAt) {
        double opening = Math.max(0.0, Math.min(1.0, (now - openedAt) / FADE_TICKS));
        double closing = closesAt < 0 ? 1 : Math.max(0.0, Math.min(1.0, 1 - (now - closesAt) / FADE_TICKS));
        return Math.min(opening, closing);
    }
}
