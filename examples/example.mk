CFLAGS  = -Wall -Werror -std=c99
LDFLAGS =
LDLIBS  = -lgsl -lgslcblas -lm
OUT     = prog
OBJ     = main.o

.PHONY: release debug clean

release: CFLAGS := $(CFLAGS) -O2
release: $(OUT)

debug:   CFLAGS := $(CFLAGS) -O0 -g3 -ggdb -pg
debug:   $(OUT)

clean:
	$(RM) $(OBJ) $(OBJ:.o=.d) $(OUT)

$(OUT): $(OBJ)
	$(CC) $(LDFLAGS) -o $@ $^ $(LDLIBS)

%.o: %.c %.d
	$(CC) $(CFLAGS) -c -o $@ $<

%.d: %.c
	$(CC) $(CFLAGS) -MF $@ -MM $<

ifneq ($(MAKECMDGOALS),clean)
-include $(OBJ:.o=.d)
endif
