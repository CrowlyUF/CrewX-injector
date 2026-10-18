# Teste do callback JVMTI

Executado em uma JVM Java 8 local, sem Minecraft. Usa diretamente native_trc, native_gcb e native_scb de dllmain.c.

Em um Developer Command Prompt x64, copie estes fontes para uma pasta temporária preservando a referência a dllmain.c (ou compile callback_smoke.c pelo caminho original). Compile CallbackSmoke.java com javac -d apontando para a pasta temporária. Compile callback_smoke.c com cl /W4, os includes JNI/JVMTI do JDK (include e include/win32), e user32.lib. Execute o EXE com essa pasta temporária como diretório atual e o caminho absoluto de jvm.dll do Java 8 como único argumento.

Verifica: callback sem patch, exceção, bytes inválidos, exatamente um callback na instalação, manutenção em nova retransformation, substituição que falha, substituição válida e restauração. Não verifica os hooks específicos do Minecraft, FPS nem aceitação de blocos pelo servidor.
