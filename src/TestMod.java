import arc.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import mindustry.ui.dialogs.*;

public class TestMod extends Mod {
    public TestMod() {
        Events.on(ClientLoadEvent.class, e -> {
            BaseDialog dialog = new BaseDialog("Test Mod");
            dialog.cont.add("IT WORKS!").row();
            dialog.buttons.button("OK", dialog::hide).size(100, 50);
            dialog.show();
        });
    }
                  }
