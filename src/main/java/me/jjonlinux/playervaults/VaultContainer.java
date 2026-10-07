package me.jjonlinux.playervaults;

import net.minecraft.world.SimpleContainer;

// The 54 slots of one open vault. Remembers whether anything changed since the
// last save so we only write files when we need to.
public class VaultContainer extends SimpleContainer {
    private boolean dirty;

    public VaultContainer(int size) {
        super(size);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        this.dirty = true;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }
}
