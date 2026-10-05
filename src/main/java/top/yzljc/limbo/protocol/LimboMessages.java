package top.yzljc.limbo.protocol;

import java.util.List;

public final class LimboMessages {
    private LimboMessages() {}
    public static final List<String> WELCOME = List.of(
            "§cYou were spawned in Limbo.",
            "§b/limbo for more information.");
    public static final List<String> HELP = List.of(
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "§cThe lobby you attempted to join was full or offline.",
            "§eBecause of this, you were routed to Limbo, a subset of your",
            "§eown imagination.",
            "§dThis place doesn't exist anywhere, and you can stay here as",
            "§dlong as you'd like.",
            "§6To return to \"reality\" use §b/lobby GAME",
            "§cExamples: /lobby, /lobby skywars, /lobby arcade",
            "§4Watch out, though, as there are things that live in Limbo.");
}
