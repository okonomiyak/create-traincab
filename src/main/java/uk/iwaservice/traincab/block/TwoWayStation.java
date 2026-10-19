package uk.iwaservice.traincab.block;

/** Added to Create's GlobalStation by GlobalStationMixin: a 高度駅 set to 双方向 lets trains stop from either side. */
public interface TwoWayStation {

    boolean traincab$twoWay();

    void traincab$setTwoWay(boolean twoWay);
}
