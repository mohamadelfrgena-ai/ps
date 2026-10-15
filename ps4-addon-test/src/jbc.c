#include "jbc.h"

#include <stdarg.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/socket.h>
#include <unistd.h>

typedef enum KmemKind { USERSPACE, KERNEL_HEAP, KERNEL_TEXT } KmemKind;

static int k_kcall(void* td, uint64_t** uap) {
    (void)td;
    uint64_t* args = uap[1];
    args[0] = ((uint64_t(*)(uint64_t, uint64_t, uint64_t, uint64_t, uint64_t, uint64_t))args[0])(
        args[1], args[2], args[3], args[4], args[5], args[6]);
    return 0;
}

__asm__("jbc_kexec:\nmov $11, %rax\nmov %rcx, %r10\nsyscall\nret");
extern void jbc_kexec(void*, void*);

__asm__("jbc_k_get_td:\nmov %gs:0, %rax\nret");
extern char jbc_k_get_td[];

__asm__("jbc_k_kcpy:\nmov %rdx, %rcx\nrep movsb\nret");
extern char jbc_k_kcpy[];

__asm__("jbc_sys_open:\nmov $5, %rax\nmov %rcx, %r10\nsyscall\nret");
extern int jbc_sys_open(const char*, int);

__asm__("jbc_sys_close:\nmov $6, %rax\nmov %rcx, %r10\nsyscall\nret");
extern int jbc_sys_close(int);

static uint64_t jbc_krw_kcall(uint64_t fn, ...) {
    va_list v;
    va_start(v, fn);
    uint64_t uap[7] = {fn};
    for (int i = 1; i <= 6; i++)
        uap[i] = va_arg(v, uint64_t);
    va_end(v);
    jbc_kexec((void*)k_kcall, uap);
    return uap[0];
}

static uintptr_t jbc_krw_get_td(void) {
    return jbc_krw_kcall((uintptr_t)jbc_k_get_td);
}

static int have_mira = -1;
static int mira_socket[2];

static int do_check_mira(void) {
    if (socketpair(AF_UNIX, SOCK_STREAM, 0, mira_socket))
        return 0;
    if (write(mira_socket[1], (void*)jbc_krw_get_td(), 1) == 1) {
        char c;
        read(mira_socket[0], &c, 1);
        return 1;
    }
    return 0;
}

static inline bool check_mira(void) {
    if (have_mira < 0)
        have_mira = do_check_mira();
    return (bool)have_mira;
}

static inline bool check_ptr(uintptr_t p, KmemKind kind) {
    if (kind == USERSPACE)
        return p < 0x800000000000ULL;
    else if (kind == KERNEL_HEAP)
        return p >= 0xffff800000000000ULL && p < 0xffffffff00000000ULL;
    else if (kind == KERNEL_TEXT)
        return p >= 0xffffffff00000000ULL && p < 0xfffffffffffff000ULL;
    else
        return false;
}

static int kcpy_mira(uintptr_t dst, uintptr_t src, size_t sz) {
    while (sz > 0) {
        size_t chk = (sz > 64 ? 64 : sz);
        if (write(mira_socket[1], (void*)src, chk) != (ssize_t)chk)
            return -1;
        if (read(mira_socket[0], (void*)dst, chk) != (ssize_t)chk)
            return -1;
        dst += chk;
        src += chk;
        sz -= chk;
    }
    return 0;
}

static int jbc_krw_memcpy(uintptr_t dst, uintptr_t src, size_t sz, KmemKind kind) {
    if (sz == 0)
        return 0;
    bool u1 = check_ptr(dst, USERSPACE) && check_ptr(dst + sz - 1, USERSPACE);
    bool ok1 = check_ptr(dst, kind) && check_ptr(dst + sz - 1, kind);
    bool u2 = check_ptr(src, USERSPACE) && check_ptr(src + sz - 1, USERSPACE);
    bool ok2 = check_ptr(src, kind) && check_ptr(src + sz - 1, kind);
    if (!((u1 || ok1) && (u2 || ok2)))
        return -1;
    if (u1 && u2)
        return -1;
    if (check_mira())
        return kcpy_mira(dst, src, sz);
    jbc_krw_kcall((uintptr_t)jbc_k_kcpy, dst, src, sz);
    return 0;
}

static uint64_t jbc_krw_read64(uintptr_t p, KmemKind kind) {
    uint64_t ans;
    if (jbc_krw_memcpy((uintptr_t)&ans, p, sizeof(ans), kind))
        return (uint64_t)-1;
    return ans;
}

static uintptr_t prison0;
static uintptr_t rootvnode;

static int resolve(void) {
restart:;
    uintptr_t td = jbc_krw_get_td();
    uintptr_t proc = jbc_krw_read64(td + 8, KERNEL_HEAP);
    for (;;) {
        int pid;
        if (jbc_krw_memcpy((uintptr_t)&pid, proc + 0xb0, sizeof(pid), KERNEL_HEAP))
            goto restart;
        if (pid == 1)
            break;
        uintptr_t proc2 = jbc_krw_read64(proc, KERNEL_HEAP);
        uintptr_t proc1 = jbc_krw_read64(proc2 + 8, KERNEL_HEAP);
        if (proc1 != proc)
            goto restart;
        proc = proc2;
    }
    uintptr_t pid1_ucred = jbc_krw_read64(proc + 0x40, KERNEL_HEAP);
    uintptr_t pid1_fd = jbc_krw_read64(proc + 0x48, KERNEL_HEAP);
    if (jbc_krw_memcpy((uintptr_t)&prison0, pid1_ucred + 0x30, sizeof(prison0), KERNEL_HEAP))
        return -1;
    if (jbc_krw_memcpy((uintptr_t)&rootvnode, pid1_fd + 0x18, sizeof(rootvnode), KERNEL_HEAP)) {
        prison0 = 0;
        return -1;
    }
    return 0;
}

uintptr_t jbc_get_prison0(void) {
    if (!prison0)
        resolve();
    return prison0;
}

uintptr_t jbc_get_rootvnode(void) {
    if (!rootvnode)
        resolve();
    return rootvnode;
}

static inline int ppcopyout(void* u1, void* u2, uintptr_t k) {
    return jbc_krw_memcpy((uintptr_t)u1, k, (uintptr_t)u2 - (uintptr_t)u1, KERNEL_HEAP);
}

static inline int ppcopyin(const void* u1, const void* u2, uintptr_t k) {
    return jbc_krw_memcpy(k, (uintptr_t)u1, (uintptr_t)u2 - (uintptr_t)u1, KERNEL_HEAP);
}

int jbc_get_cred(struct jbc_cred* ans) {
    uintptr_t td = jbc_krw_get_td();
    uintptr_t proc = jbc_krw_read64(td + 8, KERNEL_HEAP);
    uintptr_t ucred = jbc_krw_read64(proc + 0x40, KERNEL_HEAP);
    uintptr_t fd = jbc_krw_read64(proc + 0x48, KERNEL_HEAP);

    if (ppcopyout(&ans->uid, 1 + &ans->svuid, ucred + 4) ||
        ppcopyout(&ans->rgid, 1 + &ans->svgid, ucred + 20) ||
        ppcopyout(&ans->prison, 1 + &ans->prison, ucred + 0x30) ||
        ppcopyout(&ans->cdir, 1 + &ans->jdir, fd + 0x10) ||
        ppcopyout(&ans->sceProcType, 1 + &ans->sceProcCap, ucred + 88))
        return -1;

    return 0;
}

static int jbc_set_cred_internal(const struct jbc_cred* ans) {
    uintptr_t td = jbc_krw_get_td();
    uintptr_t proc = jbc_krw_read64(td + 8, KERNEL_HEAP);
    uintptr_t ucred = jbc_krw_read64(proc + 0x40, KERNEL_HEAP);
    uintptr_t fd = jbc_krw_read64(proc + 0x48, KERNEL_HEAP);

    if (ppcopyin(&ans->uid, 1 + &ans->svuid, ucred + 4) ||
        ppcopyin(&ans->rgid, 1 + &ans->svgid, ucred + 20) ||
        ppcopyin(&ans->prison, 1 + &ans->prison, ucred + 0x30) ||
        ppcopyin(&ans->cdir, 1 + &ans->jdir, fd + 0x10) ||
        ppcopyin(&ans->sceProcType, 1 + &ans->sceProcCap, ucred + 88))
        return -1;
    return 0;
}

int jbc_jailbreak_cred(struct jbc_cred* ans) {
    uintptr_t p0 = jbc_get_prison0();
    if (!p0)
        return -1;
    uintptr_t rv0 = jbc_get_rootvnode();
    if (!rv0)
        return -1;

    ans->sceProcCap = 0xffffffffffffffffULL;
    ans->sceProcType = 0x3801000000000013ULL;
    ans->sonyCred = 0xffffffffffffffffULL;

    ans->uid = ans->ruid = ans->svuid = ans->rgid = ans->svgid = 0;
    ans->prison = p0;
    ans->cdir = ans->rdir = ans->jdir = rv0;
    return 0;
}

static int jbc_open_this(const struct jbc_cred* cred0, uintptr_t vnode) {
    if (!vnode)
        return -1;
    struct jbc_cred cred = *cred0;
    jbc_jailbreak_cred(&cred);
    cred.cdir = cred.rdir = cred.jdir = vnode;
    jbc_set_cred_internal(&cred);
    int ans = jbc_sys_open("/", 0);
    jbc_set_cred_internal(cred0);
    return ans;
}

static int swap64(uintptr_t p1, KmemKind k1, uintptr_t p2, KmemKind k2) {
    uintptr_t v1 = jbc_krw_read64(p1, k1);
    uintptr_t v2 = jbc_krw_read64(p2, k2);
    if (ppcopyin(&v2, 1 + &v2, p1) || ppcopyin(&v1, 1 + &v1, p2))
        return -1;
    return 0;
}

static int return0(void) {
    return 0;
}

static void* fake_vtable[16] = {};

int jbc_set_cred(const struct jbc_cred* newp) {
    struct jbc_cred old, neww = *newp;
    if (jbc_get_cred(&old))
        return -1;
    if (old.cdir && !neww.cdir)
        neww.cdir = jbc_get_rootvnode();
    if (old.rdir && !neww.rdir)
        neww.rdir = jbc_get_rootvnode();
    if (old.jdir && !neww.jdir)
        neww.jdir = jbc_get_rootvnode();
    int cdir_fd = jbc_open_this(&old, neww.cdir);
    int rdir_fd = jbc_open_this(&old, neww.rdir);
    int jdir_fd = jbc_open_this(&old, neww.jdir);
    struct jbc_cred elevated = neww;
    elevated.cdir = old.cdir;
    elevated.jdir = old.jdir;
    elevated.rdir = old.rdir;
    uint32_t rc;
    if (elevated.prison) {
        if (jbc_krw_memcpy((uintptr_t)&rc, elevated.prison + 0x14, sizeof(rc), KERNEL_HEAP) &&
            jbc_krw_memcpy((uintptr_t)&rc, elevated.prison + 0x14, sizeof(rc), KERNEL_TEXT))
            return -1;
        rc++;
        if (jbc_krw_memcpy(elevated.prison + 0x14, (uintptr_t)&rc, sizeof(rc), KERNEL_HEAP) &&
            jbc_krw_memcpy(elevated.prison + 0x14, (uintptr_t)&rc, sizeof(rc), KERNEL_TEXT))
            return -1;
    }
    if (jbc_set_cred_internal(&elevated))
        return -1;
    if (old.prison) {
        if (jbc_krw_memcpy((uintptr_t)&rc, old.prison + 0x14, sizeof(rc), KERNEL_HEAP) &&
            jbc_krw_memcpy((uintptr_t)&rc, old.prison + 0x14, sizeof(rc), KERNEL_TEXT))
            return -1;
        rc--;
        if (jbc_krw_memcpy(old.prison + 0x14, (uintptr_t)&rc, sizeof(rc), KERNEL_HEAP) &&
            jbc_krw_memcpy(old.prison + 0x14, (uintptr_t)&rc, sizeof(rc), KERNEL_TEXT))
            return -1;
    }
    uintptr_t td = jbc_krw_get_td();
    uintptr_t proc = jbc_krw_read64(td + 8, KERNEL_HEAP);
    uintptr_t fd = jbc_krw_read64(proc + 0x48, KERNEL_HEAP);
    uintptr_t ofiles = jbc_krw_read64(fd, KERNEL_HEAP);
    int fds[3] = {cdir_fd, rdir_fd, jdir_fd};
    for (int i = 0; i < 3; i++) {
        if (fds[i] < 0)
            continue;
        uintptr_t file = jbc_krw_read64(ofiles + 8 * fds[i], KERNEL_HEAP);
        if (swap64(file, KERNEL_HEAP, fd + 0x10 + 8 * i, KERNEL_HEAP))
            return -1;
        if (jbc_krw_read64(file, KERNEL_HEAP) == 0) {
            if (!fake_vtable[0])
                for (size_t j = 0; j < sizeof(fake_vtable) / sizeof(fake_vtable[0]); j++)
                    fake_vtable[j] = (void*)return0;
            uintptr_t p = (uintptr_t)fake_vtable;
            if (ppcopyin(&p, 1 + &p, file + 8))
                return -1;
        }
        jbc_sys_close(fds[i]);
    }
    return 0;
}
