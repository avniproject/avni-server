package org.avni.server.domain;

import java.util.*;

public class AddressLevelTypes extends ArrayList<AddressLevelType> {
    public AddressLevelTypes(AddressLevelType ... addressLevelTypes) {
        super(Arrays.asList(addressLevelTypes));
    }

    public AddressLevelTypes(List<AddressLevelType> addressLevelTypes) {
        super(addressLevelTypes);
    }

    public AddressLevelTypes getLowToHigh() {
        AddressLevelTypes temp = new AddressLevelTypes(this);
        temp.sort(Comparator.comparingDouble(AddressLevelType::getLevel));
        return temp;
    }

    public AddressLevelTypes getHighToLow() {
        AddressLevelTypes temp = this.getLowToHigh();
        Collections.reverse(temp);
        return temp;
    }
}
