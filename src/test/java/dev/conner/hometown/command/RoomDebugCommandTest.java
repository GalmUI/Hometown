package dev.conner.hometown.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomDebugCommandTest {
    private CommandDispatcher<CommandSourceStack> dispatcher;
    private CommandSourceStack source;
    private ServerChunkCache chunks;
    private boolean bedPresent=true, outdoors=false;
    private String output;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void setup() throws Exception {
        dispatcher=new CommandDispatcher<>(); var event=mock(RegisterCommandsEvent.class);
        when(event.getDispatcher()).thenReturn(dispatcher); HometownDebugCommands.register(event);
        source=mock(CommandSourceStack.class); when(source.hasPermission(2)).thenReturn(true);
        var player=mock(ServerPlayer.class); when(source.getPlayerOrException()).thenReturn(player);
        when(player.blockPosition()).thenReturn(new BlockPos(2,1,2));
        var level=mock(ServerLevel.class); when(source.getLevel()).thenReturn(level);
        when(level.getHeight()).thenReturn(384); when(level.getMinBuildHeight()).thenReturn(-64);
        chunks=mock(ServerChunkCache.class); when(level.getChunkSource()).thenReturn(chunks);
        var chunk=mock(LevelChunk.class); when(chunks.getChunkNow(anyInt(),anyInt())).thenReturn(chunk);
        when(chunk.getHeight(eq(Heightmap.Types.WORLD_SURFACE),anyInt(),anyInt())).thenAnswer(c->outdoors?0:4);
        when(chunk.getBlockState(any(BlockPos.class))).thenAnswer(c->{
            BlockPos p=c.getArgument(0);
            if(bedPresent && p.getX()==2 && p.getY()==1 && (p.getZ()==2 || p.getZ()==3))
                return Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH)
                        .setValue(BedBlock.PART,p.getZ()==2?BedPart.HEAD:BedPart.FOOT);
            boolean interior=p.getX()>0 && p.getX()<6 && p.getY()>0 && p.getY()<4 && p.getZ()>0 && p.getZ()<6;
            return (interior || outdoors?Blocks.AIR:Blocks.STONE).defaultBlockState();
        });
        doAnswer(c->{ Supplier<Component> message=c.getArgument(0); output=message.get().getString(); return null; })
                .when(source).sendSuccess(any(),eq(false));
        doAnswer(c->{ output=((Component)c.getArgument(0)).getString(); return null; }).when(source).sendFailure(any());
    }
    @Test void operatorGetsReadableCompleteDiagnostic() throws Exception {
        assertEquals(1,dispatcher.execute("hometown debug room",source));
        assertTrue(output.contains("Enclosed: Yes")); assertTrue(output.contains("Interior Volume: 75"));
        assertTrue(output.contains("Beds in Room: 1"));
        assertTrue(mockingDetails(chunks).getInvocations().stream().allMatch(i->i.getMethod().getName().equals("getChunkNow")));
    }
    @Test void nonOperatorCannotRunDiagnostic() {
        when(source.hasPermission(2)).thenReturn(false);
        assertThrows(CommandSyntaxException.class,()->dispatcher.execute("hometown debug room",source));
        verifyNoInteractions(chunks);
    }
    @Test void noNearbyBedHasClearMessage() throws Exception {
        bedPresent=false; assertEquals(0,dispatcher.execute("hometown debug room",source));
        assertTrue(output.contains("No intact bed found"));
    }
    @Test void outdoorFailureLabelsPartialCounts() throws Exception {
        outdoors=true; assertEquals(0,dispatcher.execute("hometown debug room",source));
        assertTrue(output.contains("ESCAPED_TO_OUTSIDE")); assertTrue(output.contains("Enclosed: No"));
        assertTrue(output.contains("partial"));
    }
}
