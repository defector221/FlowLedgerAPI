package com.flowledger.auth.service;
import com.flowledger.auth.entity.User; import com.flowledger.auth.repository.UserRepository; import com.flowledger.common.security.UserPrincipal; import org.springframework.security.core.userdetails.*; import org.springframework.stereotype.Service; import java.util.*;
@Service public class CustomUserDetailsService implements UserDetailsService {
 private final UserRepository users; public CustomUserDetailsService(UserRepository users){this.users=users;}
 public UserDetails loadUserByUsername(String id){return load(UUID.fromString(id));}
 public UserPrincipal load(UUID id){User u=users.findByIdAndActiveTrue(id).orElseThrow(()->new UsernameNotFoundException("User not found"));
  Set<String> a=new HashSet<>(); u.getRoles().forEach(r->{a.add("ROLE_"+r.getCode());r.getPermissions().forEach(p->a.add(p.getCode()));});
  return UserPrincipal.of(u.getId(),u.getOrganizationId(),u.getEmail(),u.getPasswordHash(),a,u.isActive());}
}
