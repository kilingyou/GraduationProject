package com.scm.integration.blockchain;

import com.scm.module.system.entity.SysUser;
import com.scm.module.system.mapper.SysUserMapper;
import com.scm.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SmartContractInvokeServiceTest {

    private ObjectProvider<FiscoBcosBlockchainAnchorService> provider;
    private FiscoBcosBlockchainAnchorService fisco;
    private SysUserMapper userMapper;
    private SmartContractInvokeService service;

    @BeforeEach
    void setUp() {
        provider = mock(ObjectProvider.class);
        fisco = mock(FiscoBcosBlockchainAnchorService.class);
        userMapper = mock(SysUserMapper.class);
        service = new SmartContractInvokeService(provider, userMapper);
        SecurityContextHolder.clearContext();
    }

    @Test
    void createProductionRequest_shouldCallContractWithResolvedAddress() throws Exception {
        mockLoginUser(100L, "0xaaa");
        when(provider.getIfAvailable()).thenReturn(fisco);
        when(fisco.isAvailable()).thenReturn(true);
        when(fisco.sendTransactionByPrivateKey(anyString(), eq("createProductionRequest"), anyList())).thenReturn("0xabc");
        when(userMapper.selectById(99L)).thenReturn(withAddress("0x1111111111111111111111111111111111111111"));
        when(userMapper.selectById(100L)).thenReturn(withPrivateKey("abc123"));

        String tx = service.createProductionRequest("OID-1", 99L, "bomHash", 10, "designHash",
                LocalDate.of(2026, 4, 8), "qualityHash");
        assertEquals("0xabc", tx);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(fisco).sendTransactionByPrivateKey(eq("abc123"), eq("createProductionRequest"), captor.capture());
        List params = captor.getValue();
        assertEquals("OID-1", params.get(0));
        assertEquals("0x1111111111111111111111111111111111111111", params.get(1));
        assertEquals(10L, params.get(3));
    }

    @Test
    void logTransfer_shouldUseZeroAddressWhenUserHasNoChainAddress() throws Exception {
        mockLoginUser(100L, "0xaaa");
        when(provider.getIfAvailable()).thenReturn(fisco);
        when(fisco.isAvailable()).thenReturn(true);
        when(fisco.sendTransactionByPrivateKey(anyString(), eq("logTransfer"), anyList())).thenReturn("0xdef");
        when(userMapper.selectById(7L)).thenReturn(new SysUser());
        when(userMapper.selectById(100L)).thenReturn(withPrivateKey("abc123"));

        String tx = service.logTransfer("SN-1", "T-1", 7L, "SHIP");
        assertEquals("0xdef", tx);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(fisco).sendTransactionByPrivateKey(eq("abc123"), eq("logTransfer"), captor.capture());
        List params = captor.getValue();
        assertEquals("0x0000000000000000000000000000000000000000", params.get(2));
    }

    @Test
    void shouldThrowWhenFiscoUnavailable() throws Exception {
        when(provider.getIfAvailable()).thenReturn(fisco);
        when(fisco.isAvailable()).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                service.registerSale("SN-1", "cHash", "iHash"));

        verify(fisco, never()).sendTransactionByPrivateKey(anyString(), anyString(), anyList());
    }

    private void mockLoginUser(Long userId, String chainAddr) {
        LoginUser loginUser = new LoginUser(userId, "u", "p", "supplier", chainAddr, Collections.emptyList(), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities())
        );
    }

    private SysUser withAddress(String addr) {
        SysUser user = new SysUser();
        user.setBlockchainAddr(addr);
        return user;
    }

    private SysUser withPrivateKey(String privateKeyHex) {
        SysUser user = new SysUser();
        user.setPrivateKeyEnc(java.util.Base64.getEncoder().encodeToString(privateKeyHex.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return user;
    }
}
