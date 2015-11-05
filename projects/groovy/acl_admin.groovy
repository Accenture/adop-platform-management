import hudson.*
import hudson.model.*
import hudson.security.*
import jenkins.*
import jenkins.model.*
import java.util.*
import com.michelin.cio.hudson.plugins.rolestrategy.*
import java.lang.reflect.*

// Get Current Auth Strategy
def jenkinsInstance = Jenkins.getInstance()
def currentAuthenticationStrategy = Hudson.instance.getAuthorizationStrategy()

if (currentAuthenticationStrategy instanceof RoleBasedAuthorizationStrategy) {
    // Constants
    def roleName = "admin"

    // Variables
    def workspaceViewerFolderRoleName = build.getEnvironment(listener).get('WORKSPACE_NAME') + ".viewer.Folder"
    def namespace = build.getEnvironment(listener).get('WORKSPACE_NAME') + "/" + build.getEnvironment(listener).get('PROJECT_NAME')
    def ldapNamespace = build.getEnvironment(listener).get('WORKSPACE_NAME') + "." + build.getEnvironment(listener).get('PROJECT_NAME')
    
    def ldapGroupName = ldapNamespace + "." + roleName
    RoleBasedAuthorizationStrategy roleBasedAuthenticationStrategy = currentAuthenticationStrategy;

    // Make the method assignRole accessible
    Method assignRoleMethod = RoleBasedAuthorizationStrategy.class.getDeclaredMethod("assignRole", String.class, Role.class, String.class);
    assignRoleMethod.setAccessible(true);
    
    /// Workspace Folder
    Role workspaceFolderRole = roleBasedAuthenticationStrategy.getRoleMap(RoleBasedAuthorizationStrategy.PROJECT).getRole(workspaceViewerFolderRoleName);
    roleBasedAuthenticationStrategy.assignRole(RoleBasedAuthorizationStrategy.PROJECT,workspaceFolderRole,ldapGroupName);

    /// Project Folder
    def folderRoleName = namespace + "." + roleName + ".Folder"
    def folderRolePattern = "^" + namespace + "\$"
    
    // Create set of permissions
    Set<Permission> folderPermissions = new HashSet<Permission>();
    folderPermissions.add(Permission.fromId("hudson.model.Item.Create"));
    folderPermissions.add(Permission.fromId("hudson.model.Run.Delete"));
    folderPermissions.add(Permission.fromId("hudson.model.Item.Workspace"));
    folderPermissions.add(Permission.fromId("com.cloudbees.plugins.credentials.CredentialsProvider.Delete"));
    folderPermissions.add(Permission.fromId("com.cloudbees.plugins.credentials.CredentialsProvider.ManageDomains"));
    folderPermissions.add(Permission.fromId("com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl.Retrigger"));
    folderPermissions.add(Permission.fromId("hudson.model.Item.Cancel"));
    folderPermissions.add(Permission.fromId("hudson.model.Item.Read"));
    folderPermissions.add(Permission.fromId("com.cloudbees.plugins.credentials.CredentialsProvider.View"));
    folderPermissions.add(Permission.fromId("com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl.ManualTrigger"));
    folderPermissions.add(Permission.fromId("com.cloudbees.plugins.credentials.CredentialsProvider.Create"));
    folderPermissions.add(Permission.fromId("com.cloudbees.plugins.credentials.CredentialsProvider.Update"));
    folderPermissions.add(Permission.fromId("hudson.model.Item.Build"));
    folderPermissions.add(Permission.fromId("hudson.scm.SCM.Tag"));
    folderPermissions.add(Permission.fromId("hudson.model.Item.Discover"));
    folderPermissions.add(Permission.fromId("hudson.model.Run.Update"));

    // Create the Role
    Role folderRole = new Role(folderRoleName,folderRolePattern,folderPermissions);
    roleBasedAuthenticationStrategy.addRole(RoleBasedAuthorizationStrategy.PROJECT,folderRole);

    // Assign the role
    roleBasedAuthenticationStrategy.assignRole(RoleBasedAuthorizationStrategy.PROJECT,folderRole,ldapGroupName);
    
    /// Project Folder Contents
    def contentRoleName = namespace + "." + roleName + ".Content"
    def contentRolePattern = "^" + namespace + "/.*"
    
    // Create set of permissions
    Set<Permission> contentPermissions = new HashSet<Permission>();
    contentPermissions.add(Permission.fromId("hudson.model.Item.Create"));
    contentPermissions.add(Permission.fromId("hudson.model.Run.Delete"));
    contentPermissions.add(Permission.fromId("hudson.model.Item.Workspace"));
    contentPermissions.add(Permission.fromId("com.cloudbees.plugins.credentials.CredentialsProvider.Delete"));
    contentPermissions.add(Permission.fromId("com.cloudbees.plugins.credentials.CredentialsProvider.ManageDomains"));
    contentPermissions.add(Permission.fromId("com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl.Retrigger"));
    contentPermissions.add(Permission.fromId("hudson.model.Item.Configure"));
    contentPermissions.add(Permission.fromId("hudson.model.Item.Cancel"));
    contentPermissions.add(Permission.fromId("hudson.model.Item.Delete"));
    contentPermissions.add(Permission.fromId("hudson.model.Item.Read"));
    contentPermissions.add(Permission.fromId("com.cloudbees.plugins.credentials.CredentialsProvider.View"));
    contentPermissions.add(Permission.fromId("com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl.ManualTrigger"));
    contentPermissions.add(Permission.fromId("com.cloudbees.plugins.credentials.CredentialsProvider.Create"));
    contentPermissions.add(Permission.fromId("com.cloudbees.plugins.credentials.CredentialsProvider.Update"));
    contentPermissions.add(Permission.fromId("hudson.model.Item.Build"));
    contentPermissions.add(Permission.fromId("hudson.scm.SCM.Tag"));
    contentPermissions.add(Permission.fromId("hudson.model.Item.Move"));
    contentPermissions.add(Permission.fromId("hudson.model.Item.Discover"));
    contentPermissions.add(Permission.fromId("hudson.model.Run.Update"));

    // Create the Role
    Role contentRole = new Role(contentRoleName,contentRolePattern,contentPermissions);
    roleBasedAuthenticationStrategy.addRole(RoleBasedAuthorizationStrategy.PROJECT,contentRole);

    // Assign the role
    roleBasedAuthenticationStrategy.assignRole(RoleBasedAuthorizationStrategy.PROJECT,contentRole,ldapGroupName);
    
    println "Admin role created...OK"

    // Save the state
    println "Saving changes."
    jenkinsInstance.save()
} else {
  println "Authorisation strategy not set to RoleBasedAuthorizationStrategy, skipping configuration"
  return
}
