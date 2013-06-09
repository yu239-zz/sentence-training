function pid = getpid()
T = textread('/proc/self/stat','%s');
pid = num2str(T{1});
