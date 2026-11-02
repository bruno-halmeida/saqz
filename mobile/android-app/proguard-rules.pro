# O ui_action e o screen_view usam o nome da classe (analyticsName). Sem estas regras o R8 renomeia as classes, o nome
# chega vazio ao Google Analytics e as telas do Android aparecem como (not set). O iOS mantém os nomes.
-keepnames class * implements androidx.navigation3.runtime.NavKey
-keepnames class * extends br.com.saqz.core.common.mvi.MviViewModel
-keepnames class br.com.saqz.**Intent
-keepnames class br.com.saqz.**Intent$*
