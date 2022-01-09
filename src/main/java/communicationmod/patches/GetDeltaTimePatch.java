package communicationmod.patches;

import com.badlogic.gdx.backends.lwjgl.LwjglGraphics;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

public class GetDeltaTimePatch {
    @SpirePatch(clz = LwjglGraphics.class, method = "getDeltaTime")
    public static class DeltaPatch {
        public static ExprEditor Instrument() {
            return new ExprEditor() {
                public void edit(FieldAccess f) throws CannotCompileException {
                    if (f.getFieldName().equals("deltaTime"))
                        f.replace("{ $_ = communicationmod.CommunicationMod.getConstantDelta(); }");
                }
            };
        }
    }
}
