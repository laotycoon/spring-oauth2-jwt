package oauth.authorization;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.jwt.crypto.sign.RsaSigner;
import org.springframework.security.jwt.crypto.sign.Signer;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.security.oauth2.provider.token.TokenEnhancerChain;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;

import oauth.authorization.security.SecretKeyProvider;
import oauth.authorization.token.store.JweAccessTokenConverter;

@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {
	
	@Autowired
    private DataSource dataSource;
	
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private SecretKeyProvider keyProvider;
    
    @Bean
    public JweAccessTokenConverter accessTokenConverter() throws CertificateException, IOException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
        JweAccessTokenConverter converter = new JweAccessTokenConverter();
        converter.setPublicKey(keyProvider.getClientPublicKey())
		.setJweAlgo(JWEAlgorithm.RSA_OAEP_256)
		.setEncMethod(EncryptionMethod.A256GCM);
        Signer signer = new RsaSigner(((RSAPrivateKey) keyProvider.getKey()));
        converter.setSigner(signer);
        converter.setVerifierKey(keyProvider.getPublicKey());
        return converter;
    }
    
    @Bean
    public TokenStore tokenStore() throws CertificateException, IOException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
        return new JwtTokenStore(accessTokenConverter());
    }
    
    @Bean
    public TokenEnhancer tokenEnhancer() {
        return new CustomTokenEnhancer();
    }
    
    @Bean
    public TokenEnhancer removeClaimsTokenEnhancer() {
        return new RemoveClaimsTokenEnhancer();
    }
    
    @Bean
    public DefaultTokenServices tokenServices() throws CertificateException, IOException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
    	TokenEnhancerChain tokenEnhancerChain = new TokenEnhancerChain();
    	tokenEnhancerChain.setTokenEnhancers(Arrays.asList(tokenEnhancer(), accessTokenConverter(), removeClaimsTokenEnhancer()));
        DefaultTokenServices tokenServices = new DefaultTokenServices();
        tokenServices.setTokenStore(tokenStore());
        tokenServices.setSupportRefreshToken(true);
        tokenServices.setTokenEnhancer(tokenEnhancerChain);
        return tokenServices;
    }
    
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        endpoints
            .authenticationManager(authenticationManager)//need this for "password" grant type
            .tokenServices(tokenServices())
            .tokenStore(tokenStore());
    }
    
    @Override
    public void configure(AuthorizationServerSecurityConfigurer oauthServer) throws Exception {
    	oauthServer
    		.tokenKeyAccess("permitAll()")
    		.checkTokenAccess("isAuthenticated()");
    }
    
    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
    	clients.jdbc(dataSource);
    }
    

}
