import java.lang.reflect.Method;
import java.util.UUID;

/** 時間と接触の仕様をMinecraft起動なしで実行する。 */
public final class YamatoRiftRulesChecks {
    private static Class<?> geometry;
    private static Class<?> rules;
    public static void main(String[] args) throws Exception {
        try {
            geometry = Class.forName("dev.ssscfw.venusmod.yamato.YamatoRiftGeometry");
            rules = Class.forName("dev.ssscfw.venusmod.yamato.YamatoRiftRules");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("閻魔刀の隙間の形状・寿命・再進入規則が未実装", missing);
        }
        check(inside(0, 0), "center");
        check(inside(1.25, 0) && inside(0, 1.6), "four cusps");
        check(!inside(0.9, 0.9), "diamond corners curve inward");
        for (int i = 0; i < 120; i++) {
            double angle = Math.PI * 2 * i / 120;
            double x = (double)geometry.getMethod("pointX", double.class).invoke(null, angle);
            double y = (double)geometry.getMethod("pointY", double.class).invoke(null, angle);
            check(inside(x * 0.999, y * 0.999), "inner outline " + i);
            check(!inside(x * 1.001, y * 1.001), "outer outline " + i);
        }
        Method intersects = geometry.getMethod("intersects", double.class,double.class,double.class,double.class,double.class,double.class);
        check((boolean)intersects.invoke(null, -.3,.3,-1.6,.2,-.1,.1), "player body intersects surface");
        check(!(boolean)intersects.invoke(null, .8,1.0,.8,1.0,-.1,.1), "empty curved corner must not teleport");
        check(!(boolean)intersects.invoke(null, -.3,.3,-1.6,.2,.19,.5), "outside portal plane");
        check(!box(.477817,-1.6,1.219239,1.077817,.2,1.819239,45,1),
                "rotated AABB: U minimum outside the depth slab must not teleport");
        check(!box(-1.077817,-1.6,-1.819239,-.477817,.2,-1.219239,45,1),
                "mirrored rotated AABB must preserve horizontal/depth correlation");
        check(!box(.477817,-1.6,1.219239,1.077817,.2,1.819239,405,1), "wrapped yaw preserves diagonal rejection");
        for (double yaw : new double[]{0,45,90,-45,180}) {
            check(box(-.3,-1.6,-.3,.3,.2,.3,yaw,1), "center body at yaw " + yaw);
            double radians = Math.toRadians(yaw);
            double x = 1.25 * Math.cos(radians), z = 1.25 * Math.sin(radians);
            check(box(x,0,z,x,0,z,yaw,1), "horizontal cusp at yaw " + yaw);
            check(box(0,1.6,0,0,1.6,0,yaw,1), "vertical cusp at yaw " + yaw);
        }
        check(box(-2,-.1,-.1,2,.1,.1,45,1), "slab crosses rectangle even when all four corners are outside");
        check(!box(.8,.8,-.1,1,1,.1,0,1), "curved vertical corner still excluded after clipping");
        check(box(-.3,-1.6,.18,.3,.2,.3,0,1), "positive depth boundary is inclusive");
        check(box(-.3,-1.6,-.3,.3,.2,-.18,0,1), "negative depth boundary is inclusive");
        check(!box(-.3,-1.6,.180001,.3,.2,.3,0,1), "outside positive slab boundary");
        check(!box(-.3,-1.6,-.3,.3,.2,-.180001,0,1), "outside negative slab boundary");
        check(!box(.19,-1.6,-.3,.3,.2,.3,90,1), "rotated plane depth is checked in world X");
        check(box(-.1,-.8,-.1,.1,.1,.1,45,.5), "partially opened portal accepts its visible center");
        check(box(.6,0,0,.7,0,0,0,.5), "partially opened portal horizontal boundary");
        check(!box(.63,0,0,.8,0,0,0,.5), "partially opened portal excludes its unscaled width");
        check(!box(0,.81,0,0,1,0,0,.5), "partially opened portal excludes its unscaled height");
        check(box(0,0,.18,0,0,.18,0,.5), "animation scale does not shrink the depth slab");
        check(!box(-.1,-.1,-.1,.1,.1,.1,0,0), "zero scale has no contact");
        check(!box(-.1,-.1,-.1,.1,.1,.1,0,-1), "negative scale has no contact");
        check(!box(-.1,-.1,-.1,.1,.1,.1,0,Double.NaN), "NaN scale rejected");
        check(!box(-.1,-.1,-.1,.1,.1,.1,0,Double.POSITIVE_INFINITY), "infinite scale rejected");
        check(!box(-.1,-.1,-.1,.1,.1,.1,Double.NaN,1), "NaN yaw rejected");
        check(!box(Double.NaN,-.1,-.1,.1,.1,.1,0,1), "NaN box rejected");
        check(!box(-.1,-.1,-.1,Double.POSITIVE_INFINITY,.1,.1,0,1), "infinite box rejected");
        check(!box(.3,-.1,-.1,-.3,.1,.1,0,1), "inverted box rejected");
        orientedGeometry();
        check(close(0,1,100,-1)==-1,"after-first waits");
        check(close(0,1,100,150)==170,"default closes 20 ticks after first transfer");
        check(close(0,180,100,150)==3750,"180-second after-first boundary");
        check(close(1,1,100,-1)==120,"fixed time starts at opening");
        check(close(1,180,100,500)==3700,"fixed never reset by transit");
        check(close(2,1,100,150)==-1,"unlimited never expires");
        Method enter=rules.getMethod("canEnter",int.class,boolean.class,boolean.class,boolean.class);
        check((boolean)enter.invoke(null,0,true,true,false),"owner passes self-only");
        check(!(boolean)enter.invoke(null,0,false,true,false),"other player denied self-only");
        check((boolean)enter.invoke(null,1,false,true,false),"other player passes players mode");
        check(!(boolean)enter.invoke(null,1,false,false,true),"mob denied players mode");
        check((boolean)enter.invoke(null,2,false,false,true),"friendly passes mixed mode");
        check(!(boolean)enter.invoke(null,2,false,false,false),"hostile denied mixed mode");
        check((boolean)enter.invoke(null,3,false,false,false),"all permits nonplayer entities");
        check(!(boolean)enter.invoke(null,99,true,true,true),"invalid access rejected");
        Method typedEnter=rules.getMethod("canEnter",int.class,boolean.class,boolean.class,boolean.class,boolean.class,boolean.class);
        for (int access=4;access<=6;access++) {
            check((boolean)typedEnter.invoke(null,access,false,false,false,true,false)==(access!=5),"item filter "+access);
            check((boolean)typedEnter.invoke(null,access,false,false,false,false,true)==(access!=4),"experience filter "+access);
            check(!(boolean)typedEnter.invoke(null,access,true,true,false,false,false),"owner is still excluded by typed filter "+access);
            check(!(boolean)typedEnter.invoke(null,access,false,false,true,false,false),"mobs excluded by typed filter "+access);
        }
        Class<?> latch = Class.forName("dev.ssscfw.venusmod.yamato.YamatoRiftRules$ArrivalLocks");
        Object locks = latch.getConstructor().newInstance();
        UUID entity=UUID.randomUUID(),gate=UUID.randomUUID();
        latch.getMethod("arrived",UUID.class,UUID.class).invoke(locks,entity,gate);
        Method blocked=latch.getMethod("blocked",UUID.class,UUID.class);
        check((boolean)blocked.invoke(locks,entity,gate),"arrival locked");
        for(int tick=0;tick<10000;tick++) latch.getMethod("observe",UUID.class,UUID.class,boolean.class).invoke(locks,entity,gate,true);
        check((boolean)blocked.invoke(locks,entity,gate),"standing still never expires arrival lock");
        latch.getMethod("observe",UUID.class,UUID.class,boolean.class).invoke(locks,entity,gate,false);
        check(!(boolean)blocked.invoke(locks,entity,gate),"must leave to re-enter");
        check(!(boolean)blocked.invoke(locks,UUID.randomUUID(),gate),"locks per entity");
        System.out.println("YamatoRiftRulesChecks: geometry, access, 1/180 seconds, and exit-before-return passed");
    }
    private static boolean box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ,double yaw,double scale)throws Exception{
        try {
            return (boolean)geometry.getMethod("intersectsWorldBox",double.class,double.class,double.class,double.class,
                    double.class,double.class,double.class,double.class).invoke(null,minX,minY,minZ,maxX,maxY,maxZ,yaw,scale);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("回転AABBと深さスラブの正確な交差検査が未実装", missing);
        }
    }
    private static boolean inside(double x,double y)throws Exception{return (boolean)geometry.getMethod("inside",double.class,double.class).invoke(null,x,y);}
    private static void orientedGeometry() throws Exception {
        for(double yaw:new double[]{0,31,90,-135,405}) for(double pitch:new double[]{-90,-47,0,26,90})
            for(double scale:new double[]{.5,1,5}) {
                check(box3(-.1,-.1,-.1,.1,.1,.1,yaw,pitch,scale),"3D center "+yaw+"/"+pitch);
                for(double[] point:new double[][]{{0,0,0},{1.25*scale,0,0},{0,1.6*scale,0},{0,0,.18}}) {
                    double[] p=world(point[0],point[1],point[2],yaw,pitch);
                    check(box3(p[0],p[1],p[2],p[0],p[1],p[2],yaw,pitch,scale),"oriented visible cusp or depth boundary");
                }
                double[] corner=world(.9*scale,.9*scale,0,yaw,pitch);
                check(!box3(corner[0]-.001,corner[1]-.001,corner[2]-.001,corner[0]+.001,corner[1]+.001,corner[2]+.001,yaw,pitch,scale),"tilted curved empty corner");
                double[] outside=world(0,0,.19,yaw,pitch);
                check(!box3(outside[0],outside[1],outside[2],outside[0],outside[1],outside[2],yaw,pitch,scale),"normal depth excludes points beyond plane");
            }
        check(box3(-2,-.01,-2,2,.01,2,33,90,1),"horizontal plane crosses box without any corner inside curve");
        check(!box3(-.1,.181,-.1,.1,.3,.1,0,90,1),"downward gate rejects separated Y slab");
        check(box3(-.1,-.1,-1.6,.1,.1,1.6,0,90,1),"downward gate uses Z for its long axis");
        check(!box3(-.1,-.1,-.1,.1,.1,.1,0,Double.NaN,1),"NaN pitch rejected");
        // 同じAABBの回転前の偽陽性も維持して防ぐ。
        check(!box3(.477817,-1.6,1.219239,1.077817,.2,1.819239,45,0,1),"3D projection retains U/depth correlation");
        check(!box3(2,2,-.01,2+1e-12,2+1e-12,.01,0,0,1),"tiny outside box does not contain origin");
        check(!box3(1.0004985206216093,2.5534127912367333,-3.242142166497676,
                1.5004985206216093,3.0534127912367333,-2.742142166497676,-45,-35.264389682754654,1),
                "grazing slab leaves a tiny projected cap outside the curve");
    }
    private static double[] world(double u,double v,double depth,double yaw,double pitch) {
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch),sy=Math.sin(y),cy=Math.cos(y),sp=Math.sin(p),cp=Math.cos(p);
        return new double[]{cy*u-sy*sp*v-sy*cp*depth,cp*v-sp*depth,sy*u+cy*sp*v+cy*cp*depth};
    }
    private static boolean box3(double a,double b,double c,double d,double e,double f,double yaw,double pitch,double scale)throws Exception {
        try { return (boolean)geometry.getMethod("intersectsWorldBox",double.class,double.class,double.class,double.class,
                double.class,double.class,double.class,double.class,double.class).invoke(null,a,b,c,d,e,f,yaw,pitch,scale);
        } catch(NoSuchMethodException missing) { throw new AssertionError("上下角と倍率を共有する立体接触が未実装",missing); }
    }
    private static long close(int mode,int seconds,long opened,long first)throws Exception{return (long)rules.getMethod("closingTime",int.class,int.class,long.class,long.class).invoke(null,mode,seconds,opened,first);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
