package me.sworroo.builder;

import org.bukkit.Material;

public class BlockData {
    public final Material material;
    public final byte data;
    
    public BlockData(Material material) {
        this.material = material;
        this.data = 0;
    }
    
    public BlockData(Material material, byte data) {
        this.material = material;
        this.data = data;
    }
}